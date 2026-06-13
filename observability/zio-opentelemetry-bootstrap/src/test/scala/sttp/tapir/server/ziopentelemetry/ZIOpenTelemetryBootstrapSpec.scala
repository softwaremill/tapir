package sttp.tapir.server.ziopentelemetry

import io.opentelemetry.api
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.{Attributes, AttributeKey}
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessorBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder
import io.opentelemetry.semconv.{DeploymentAttributes, ServiceAttributes}

import zio._
import zio.test._
import zio.test.Assertion._
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.{ContextStorage, IncomingContextCarrier, OutgoingContextCarrier}
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.tracing.propagation.TraceContextPropagator
import zio.telemetry.opentelemetry.metrics.Meter

import scala.collection.mutable

object ZIOpenTelemetryBootstrapSpec extends ZIOSpecDefault {

  // A minimal app for tests that doesn't actually run.
  private class TestApp(
      name: String,
      ver: Option[String] = None,
      env: Option[String] = None
  ) extends ZIOpenTelemetryAppDefault(name, ver, env) {
    def run: ZIO[Any, Any, Any] = ZIO.unit
  }

  // Provides a no-op `OpenTelemetry` plus a `ContextStorage` for materialising layers.
  private val noopOtelEnv: ULayer[api.OpenTelemetry & ContextStorage] =
    ZLayer.succeed[api.OpenTelemetry](api.OpenTelemetry.noop()) ++ OpenTelemetry.contextZIO

  // OtlpEnv suite -----------------------------------------------------------
  private val otlpEnvSuite = suite("OtlpEnv")(
    test("endpoint returns None when neither requested var nor generic var are set") {
      assertTrue(OtlpEnv.endpoint("__DEFINITELY_NOT_SET_FOR_TESTS__").isEmpty)
    },
    test("otelLogEndpoint is None when env unset") {
      assertZIO(OtlpEnv.otelLogEndpoint)(isNone)
    },
    test("otelTracesEndpoint is None when env unset") {
      assertZIO(OtlpEnv.otelTracesEndpoint)(isNone)
    },
    test("otelMetricsEndpoint is None when env unset") {
      assertZIO(OtlpEnv.otelMetricsEndpoint)(isNone)
    },
    test("logLevel returns one of the known LogLevel values") {
      val lvl = OtlpEnv.logLevel
      val known = Set[LogLevel](
        LogLevel.Trace,
        LogLevel.Debug,
        LogLevel.Info,
        LogLevel.Warning,
        LogLevel.Error,
        LogLevel.All
      )
      assertTrue(known.contains(lvl))
    },
    test("metricsTemporalityPreference defaults to CUMULATIVE when env unset") {
      assertZIO(OtlpEnv.metricsTemporalityPreference)(equalTo(AggregationTemporality.CUMULATIVE))
    },
    test("Module ADT exposes the expected env var and name fields") {
      assertTrue(
        OtlpEnv.Logging.envVar == "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT",
        OtlpEnv.Logging.name == "Logging",
        OtlpEnv.Traces.envVar == "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT",
        OtlpEnv.Traces.name == "Traces",
        OtlpEnv.Metrics.envVar == "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT",
        OtlpEnv.Metrics.name == "Metrics"
      )
    }
  )

  // attributes suite --------------------------------------------------------
  private val attributesSuite = suite("attributes")(
    test("includes service name, version and deployment environment when supplied") {
      val app = new TestApp("svc", Some("1.2.3"), Some("staging"))
      val a = app.attributes
      assertTrue(
        a.get(ServiceAttributes.SERVICE_NAME) == "svc",
        a.get(ServiceAttributes.SERVICE_VERSION) == "1.2.3",
        a.get(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME) == "staging"
      )
    },
    test("omits version and deployment environment when not supplied") {
      val app = new TestApp("svc")
      val a = app.attributes
      assertTrue(
        a.get(ServiceAttributes.SERVICE_NAME) == "svc",
        a.get(ServiceAttributes.SERVICE_VERSION) == null,
        a.get(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME) == null
      )
    },
    test("extraAttributes are merged into resource attributes") {
      val app = new TestApp("svc", Some("1.0.0"), Some("dev")) {
        override def extraAttributes: Attributes = Attributes.builder().put("stack", "zio").build()
      }
      assertTrue(app.attributes.get(AttributeKey.stringKey("stack")) == "zio")
    }
  )

  // OtelProviders.build suite -----------------------------------------------
  // Note: `OtelProviders.build` calls `buildAndRegisterGlobal()`. The OpenTelemetry SDK
  // throws on a second global registration; tests in this suite reset the global before
  // each invocation. The suite is also marked sequential to keep ordering deterministic.
  private val otelProvidersBuildSuite = suite("OtelProviders.build")(
    test("builds an SDK and invokes the customize hook exactly once when all providers are None") {
      for {
        counter <- Ref.make(0)
        _ <- ZIO.succeed(GlobalOpenTelemetry.resetForTest())
        sdk <- ZIO.succeed(
          OtelProviders(None, None, None).build(_ => ())
        )
        _ <- ZIO.succeed(GlobalOpenTelemetry.resetForTest())
        // Now repeat with a customise hook that mutates the counter.
        _ <- ZIO.succeed(
          OtelProviders(None, None, None).build { (_: OpenTelemetrySdkBuilder) =>
            // Side-effect to increment counter via Unsafe.
            Unsafe.unsafe { implicit u =>
              Runtime.default.unsafe.run(counter.update(_ + 1)).getOrThrow()
            }
          }
        )
        n <- counter.get
      } yield assertTrue(sdk != null, n == 1)
    }
  ) @@ TestAspect.sequential

  // Logging suite -----------------------------------------------------------
  private val loggingSuite = suite("Logging")(
    test("logEndpoint is None when env unset") {
      val app = new TestApp("svc")
      assertZIO(app.logEndpoint)(isNone)
    },
    test("logProvider is None when env unset") {
      val app = new TestApp("svc")
      assertZIO(ZIO.scoped(app.logProvider))(isNone)
    },
    test("logLevel delegates to OtlpEnv.logLevel by default") {
      val app = new TestApp("svc")
      assertTrue(app.logLevel == OtlpEnv.logLevel)
    }
  )

  // Metrics suite -----------------------------------------------------------
  private val metricsSuite = suite("Metrics")(
    test("meterEndpoint is None when env unset") {
      val app = new TestApp("svc")
      assertZIO(app.meterEndpoint)(isNone)
    },
    test("meterProvider is None when env unset") {
      val app = new TestApp("svc")
      assertZIO(ZIO.scoped(app.meterProvider))(isNone)
    },
    test("metricExporter constructs an exporter with CUMULATIVE preference") {
      val app = new TestApp("svc")
      val exporter = app.metricExporter("http://localhost:4317", AggregationTemporality.CUMULATIVE)
      assertTrue(exporter != null)
    },
    test("metricExporter constructs an exporter with DELTA preference") {
      val app = new TestApp("svc")
      val exporter = app.metricExporter("http://localhost:4317", AggregationTemporality.DELTA)
      assertTrue(exporter != null)
    },
    test("customMetricExporter hook is invoked when constructing the exporter") {
      for {
        flag <- Ref.make(false)
        app = new TestApp("svc") {
          override def customMetricExporter(
              builder: OtlpGrpcMetricExporterBuilder
          ): OtlpGrpcMetricExporterBuilder = {
            Unsafe.unsafe { implicit u =>
              Runtime.default.unsafe.run(flag.set(true)).getOrThrow()
            }
            builder
          }
        }
        _ = app.metricExporter("http://localhost:4317", AggregationTemporality.CUMULATIVE)
        wasCalled <- flag.get
      } yield assertTrue(wasCalled)
    },
    test("zioMetrics layer materialises with collectZioMetrics = true") {
      val app = new TestApp("svc")
      val effect = ZIO.scoped(ZIO.unit.provideSomeLayer[api.OpenTelemetry & ContextStorage](app.zioMetrics))
      effect.provideLayer(noopOtelEnv).as(assertCompletes)
    },
    test("zioMetrics layer materialises with collectZioMetrics = false") {
      val app = new TestApp("svc") {
        override def collectZioMetrics: Boolean = false
      }
      val effect = ZIO.scoped(ZIO.unit.provideSomeLayer[api.OpenTelemetry & ContextStorage](app.zioMetrics))
      effect.provideLayer(noopOtelEnv).as(assertCompletes)
    },
    test("otel4zMetrics layer produces a non-null Meter") {
      val app = new TestApp("svc")
      val effect = ZIO.serviceWith[Meter](m => assertTrue(m != null))
      effect.provideLayer(noopOtelEnv >>> (ZLayer.environment[api.OpenTelemetry & ContextStorage] ++ app.otel4zMetrics("svc")))
    }
  )

  // Traces suite ------------------------------------------------------------
  private val tracesSuite = suite("Traces")(
    test("traceEndpoint is None when env unset") {
      val app = new TestApp("svc")
      assertZIO(app.traceEndpoint)(isNone)
    },
    test("tracerProvider is None when env unset") {
      val app = new TestApp("svc")
      assertZIO(ZIO.scoped(app.tracerProvider))(isNone)
    },
    test("customizeSpanExporter / customizeSpanProcessor / customizeSdkTraceProvider hooks are invoked when an endpoint is configured") {
      for {
        exporterCalled <- Ref.make(false)
        processorCalled <- Ref.make(false)
        providerCalled <- Ref.make(false)
        app = new TestApp("svc") {
          override def traceEndpoint: ZIO[Any, Nothing, Option[String]] =
            ZIO.some("http://localhost:4317")
          override def customizeSpanExporter(
              builder: OtlpGrpcSpanExporterBuilder
          ): OtlpGrpcSpanExporterBuilder = {
            Unsafe.unsafe { implicit u =>
              Runtime.default.unsafe.run(exporterCalled.set(true)).getOrThrow()
            }
            builder
          }
          override def customizeSpanProcessor(
              builder: SimpleSpanProcessorBuilder
          ): SimpleSpanProcessorBuilder = {
            Unsafe.unsafe { implicit u =>
              Runtime.default.unsafe.run(processorCalled.set(true)).getOrThrow()
            }
            builder
          }
          override def customizeSdkTraceProvider(
              builder: SdkTracerProviderBuilder
          ): SdkTracerProviderBuilder = {
            Unsafe.unsafe { implicit u =>
              Runtime.default.unsafe.run(providerCalled.set(true)).getOrThrow()
            }
            builder
          }
        }
        provider <- ZIO.scoped(app.tracerProvider)
        e <- exporterCalled.get
        p <- processorCalled.get
        sp <- providerCalled.get
      } yield assertTrue(provider.isDefined, e, p, sp)
    },
    test("otel4zTracing layer produces a non-null Tracing") {
      val app = new TestApp("svc")
      val effect = ZIO.serviceWith[Tracing](t => assertTrue(t != null))
      effect.provideLayer(noopOtelEnv >>> (ZLayer.environment[api.OpenTelemetry & ContextStorage] ++ app.otel4zTracing("svc")))
    }
  )

  // NoopMeter suite ---------------------------------------------------------
  private val noopMeterSuite = suite("NoopMeter")(
    test("toString returns instrumentation scope name") {
      val m = noop.NoopMeter("test-scope")
      assertTrue(m.toString == "test-scope")
    },
    test("counter operations are no-ops and complete successfully") {
      val m = noop.NoopMeter("test")
      for {
        c <- m.counter("c")
        _ <- c.add(1L, Attributes.empty())
        _ <- c.inc(Attributes.empty())
        _ = c.record0(1L, Attributes.empty(), Context.current())
      } yield assertCompletes
    },
    test("histogram record completes successfully") {
      val m = noop.NoopMeter("test")
      for {
        h <- m.histogram("h", None, None, None)
        _ <- h.record(1.0, Attributes.empty())
      } yield assertCompletes
    },
    test("upDownCounter operations complete successfully") {
      val m = noop.NoopMeter("test")
      for {
        u <- m.upDownCounter("u")
        _ <- u.add(1L, Attributes.empty())
        _ <- u.inc(Attributes.empty())
        _ <- u.dec(Attributes.empty())
      } yield assertCompletes
    },
    test("observable instruments complete inside a scope") {
      val m = noop.NoopMeter("test")
      ZIO.scoped[Any](
        for {
          _ <- m.observableCounter("oc")(_ => ZIO.unit)
          _ <- m.observableGauge("og")(_ => ZIO.unit)
          _ <- m.observableUpDownCounter("oudc")(_ => ZIO.unit)
        } yield assertCompletes
      )
    }
  )

  // NoopTracing suite -------------------------------------------------------
  private val noopTracingSuite = suite("NoopTracing")(
    test("toString returns instrumentation scope name") {
      val t = noop.NoopTracing("scope")
      assertTrue(t.toString == "scope")
    },
    test("span passes the inner ZIO through unchanged") {
      val t = noop.NoopTracing("scope")
      assertZIO(t.span("s")(ZIO.succeed(42)))(equalTo(42))
    },
    test("root passes the inner ZIO through unchanged") {
      val t = noop.NoopTracing("scope")
      assertZIO(t.root("r")(ZIO.succeed(7)))(equalTo(7))
    },
    test("inSpan passes the inner ZIO through unchanged") {
      val t = noop.NoopTracing("scope")
      assertZIO(t.inSpan(Span.getInvalid(), "s")(ZIO.succeed("ok")))(equalTo("ok"))
    },
    test("extractSpan passes the inner ZIO through unchanged") {
      val t = noop.NoopTracing("scope")
      val carrier = IncomingContextCarrier.default(mutable.Map.empty[String, String])
      assertZIO(t.extractSpan(TraceContextPropagator.default, carrier, "s")(ZIO.succeed(1)))(equalTo(1))
    },
    test("getCurrentSpanUnsafe returns a non-null Span") {
      val t = noop.NoopTracing("scope")
      assertZIO(t.getCurrentSpanUnsafe.map(_ != null))(isTrue)
    },
    test("getCurrentSpanContextUnsafe completes") {
      val t = noop.NoopTracing("scope")
      assertZIO(t.getCurrentSpanContextUnsafe.map(_ != null))(isTrue)
    },
    test("spanScoped is a no-op inside a Scope") {
      val t = noop.NoopTracing("scope")
      ZIO.scoped(t.spanScoped("s")).as(assertCompletes)
    },
    test("addEvent and setAttribute overloads complete successfully") {
      val t = noop.NoopTracing("scope")
      for {
        _ <- t.addEvent("e")
        _ <- t.addEventWithAttributes("e", Attributes.empty())
        _ <- t.setAttribute("k", "v")
        _ <- t.setAttribute("k", 1L)
        _ <- t.setAttribute("k", 1.0)
        _ <- t.setAttribute("k", true)
        _ <- t.setAttribute("k", Seq("a", "b"))
        _ <- t.setAttribute(AttributeKey.stringKey("k"), "v")
      } yield assertCompletes
    },
    test("injectSpan completes") {
      val t = noop.NoopTracing("scope")
      val carrier = OutgoingContextCarrier.default(mutable.Map.empty[String, String])
      t.injectSpan(TraceContextPropagator.default, carrier).as(assertCompletes)
    },
    test("scopedEffect propagates the supplied throwable") {
      val t = noop.NoopTracing("scope")
      val ex = new RuntimeException("boom")
      assertZIO(t.scopedEffect[Int](throw ex).exit)(fails(equalTo(ex)))
    },
    test("scopedEffectTotal returns the supplied value") {
      val t = noop.NoopTracing("scope")
      assertZIO(t.scopedEffectTotal(42))(equalTo(42))
    },
    test("scopedEffectFromFuture returns the resolved value") {
      val t = noop.NoopTracing("scope")
      import scala.concurrent.Future
      assertZIO(t.scopedEffectFromFuture(_ => Future.successful(5)))(equalTo(5))
    }
  )

  // observabilityLayer end-to-end suite -------------------------------------
  // Note: building the OpenTelemetry SDK registers it globally; reset before each test.
  private val observabilityLayerSuite = suite("observabilityLayer (end-to-end with no exporters)")(
    test("materialises with default settings, exposing OpenTelemetry, ContextStorage, Meter, Tracing") {
      val app = new TestApp("e2e", Some("0.0.1"), Some("test"))
      val probe = for {
        _ <- ZIO.succeed(GlobalOpenTelemetry.resetForTest())
        otel <- ZIO.service[api.OpenTelemetry]
        cs <- ZIO.service[ContextStorage]
        meter <- ZIO.service[Meter]
        tracing <- ZIO.service[Tracing]
      } yield assertTrue(otel != null, cs != null, meter != null, tracing != null)
      ZIO.succeed(GlobalOpenTelemetry.resetForTest()) *> probe.provideLayer(app.observabilityLayer)
    },
    test("materialises with collectZioMetrics = false") {
      val app = new TestApp("e2e", Some("0.0.1"), Some("test")) {
        override def collectZioMetrics: Boolean = false
      }
      val probe = for {
        meter <- ZIO.service[Meter]
        tracing <- ZIO.service[Tracing]
      } yield assertTrue(meter != null, tracing != null)
      ZIO.succeed(GlobalOpenTelemetry.resetForTest()) *> probe.provideLayer(app.observabilityLayer)
    }
  ) @@ TestAspect.sequential

  // otel4zMetricsInterceptor suite -----------------------------------------
  private val interceptorSuite = suite("otel4zMetricsInterceptor")(
    test("returns a non-null interceptor with the default scope") {
      val app = new TestApp("svc")
      implicit val otel: api.OpenTelemetry = api.OpenTelemetry.noop()
      val interceptor = app.otel4zMetricsInterceptor()
      assertTrue(interceptor != null)
    },
    test("returns a non-null interceptor with a custom scope") {
      val app = new TestApp("svc")
      implicit val otel: api.OpenTelemetry = api.OpenTelemetry.noop()
      val interceptor = app.otel4zMetricsInterceptor("custom-scope")
      assertTrue(interceptor != null)
    }
  )

  // The full spec is run sequentially because several tests register an `OpenTelemetrySdk`
  // globally (`buildAndRegisterGlobal`); the JVM-wide `GlobalOpenTelemetry` slot is reset
  // around each such test, but parallel execution would race on it.
  def spec: Spec[Any, Any] =
    suite("zio-opentelemetry-bootstrap")(
      otlpEnvSuite,
      attributesSuite,
      otelProvidersBuildSuite,
      loggingSuite,
      metricsSuite,
      tracesSuite,
      noopMeterSuite,
      noopTracingSuite,
      observabilityLayerSuite,
      interceptorSuite
    ) @@ TestAspect.sequential
}
