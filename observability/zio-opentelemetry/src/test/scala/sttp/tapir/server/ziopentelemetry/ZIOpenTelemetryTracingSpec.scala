package sttp.tapir.server.ziopentelemetry

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

import java.nio.charset.{StandardCharsets}

import sttp.model.{Header, HeaderNames, Method, Uri}
import sttp.model.Uri._
import sttp.model.headers.Forwarded
import sttp.monad.MonadError

import sttp.tapir.TestUtil.serverRequestFromUri
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interpreter._
import sttp.tapir.server.ziopentelemetry.ZIOpenTelemetryTracing
import sttp.tapir.server.TestUtil.StringToResponseBody

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.{SpanKind, Tracer, StatusCode => OtelStatusCode}
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.semconv.ErrorAttributes

import zio._
import zio.stream._
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test._
import zio.test.Assertion._

import sttp.tapir.ztapir.RIOMonadError
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import sttp.tapir.server.ziopentelemetry.ZIOpenTelemetryTracingConfig
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.Streams
import sttp.tapir.server.interceptor.RequestResult.Response
import sttp.tapir.server.ziohttp.ZioStreamHttpResponseBody
import sttp.tapir.server.ziohttp.ZioResponseBody
import sttp.tapir.{CodecFormat, RawBodyType}
import sttp.tapir.ztapir._

object ZIOpenTelemetryTracingSpec extends ZIOSpecDefault {

  implicit val bodyListener: BodyListener[Task, String] = new BodyListener[Task, String] {
    override def onComplete(body: String)(cb: Try[Unit] => Task[Unit]): Task[String] = cb(Success(())).map(_ => body)
  }

  // Mirrors the production sttp.tapir.server.ziohttp.ZioHttpBodyListener: attaches the completion callback to the
  // response stream, so it fires (with success or failure) only once the stream has actually been consumed/sent.
  implicit val bodyStreamListener: BodyListener[Task, ZioResponseBody] = new BodyListener[Task, ZioResponseBody] {
    override def onComplete(body: ZioResponseBody)(cb: Try[Unit] => Task[Unit]): Task[ZioResponseBody] = {
      def succeed = cb(Success(()))
      def failed(cause: Cause[Throwable]) = cb(Failure(cause.squash)).orDie
      body match {
        case Right(ZioStreamHttpResponseBody(stream, contentLength)) =>
          ZIO.right(ZioStreamHttpResponseBody(stream.onError(failed) ++ ZStream.fromZIO(succeed).drain, contentLength))
        case rawOrWs => succeed.as(rawOrWs)
      }
    }
  }

  implicit val ioErr: MonadError[Task] = new RIOMonadError

  val inMemoryTracer: UIO[(InMemorySpanExporter, Tracer)] = for {
    spanExporter <- ZIO.succeed(InMemorySpanExporter.create())
    spanProcessor <- ZIO.succeed(SimpleSpanProcessor.create(spanExporter))
    tracerProvider <- ZIO.succeed(SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build())
    tracer = tracerProvider.get("TracingTest")
  } yield (spanExporter, tracer)

  val inMemoryTracerLayer: ULayer[InMemorySpanExporter with Tracer] =
    ZLayer.fromZIOEnvironment(inMemoryTracer.map { case (inMemorySpanExporter, tracer) =>
      ZEnvironment(inMemorySpanExporter).add(tracer)
    })

  def tracingMockLayer(
      logAnnotated: Boolean = false
  ): URLayer[ContextStorage, Tracing with InMemorySpanExporter with Tracer] =
    inMemoryTracerLayer >>> (Tracing.live(logAnnotated) ++ inMemoryTracerLayer)

  /** Helper: create an interpreter with the given interceptor and endpoints, run a request, return finished spans. */
  private def runRequest(
      endpoints: List[ServerEndpoint[Any, Task]],
      request: ServerRequest,
      config: ZIOpenTelemetryTracingConfig = ZIOpenTelemetryTracingConfig()
  ): ZIO[Tracing with InMemorySpanExporter, Throwable, java.util.List[SpanData]] =
    for {
      tracing <- ZIO.service[Tracing]
      exported <- ZIO.service[InMemorySpanExporter]
      _ <- ZIO.succeed(exported.reset())
      interpreter = new ServerInterpreter[Any, Task, String, NoStreams](
        _ => endpoints,
        ZIOTestRequestBody,
        StringToResponseBody,
        List(ZIOpenTelemetryTracing(tracing, config)),
        _ => ZIO.succeed(())
      )
      _ <- interpreter(request)
      spans = exported.getFinishedSpanItems()
    } yield spans

  private def runRequestStream(
      endpoints: List[ServerEndpoint[ZioStreams, Task]],
      request: ServerRequest,
      config: ZIOpenTelemetryTracingConfig
  ): ZIO[Tracing with InMemorySpanExporter, Throwable, java.util.List[SpanData]] =
    for {
      tracing <- ZIO.service[Tracing]
      exported <- ZIO.service[InMemorySpanExporter]
      _ <- ZIO.succeed(exported.reset())
      interpreter = new ServerInterpreter[ZioStreams, Task, ZioResponseBody, ZioStreams](
        _ => endpoints,
        ZIOTestRequestStreamBody,
        new sttp.tapir.server.ziohttp.ZioHttpToResponseBody(10),
        List(ZIOpenTelemetryTracing(tracing, config)),
        _ => ZIO.succeed(())
      )
      stream <- interpreter(request)
      _ <- stream match {
        case Response(serverResponse, source) =>
          serverResponse.body match {
            case Some(Right(ZioStreamHttpResponseBody(stream, None))) =>
              // draining mimics the backend sending the body; tolerate failures so the body-listener callback (which
              // marks the span as errored) runs and the finished span can be inspected
              ZIO.logDebug("streaming") *>
              stream.runDrain.catchAllCause(_ => ZIO.unit)
            case other =>
              ZIO.logDebug(s"WHat is it: $other ?")
          }

        case other =>

          ZIO.logDebug(s"What is it: $other ?")
      }

      spans = exported.getFinishedSpanItems()
    } yield spans

  /** Helper: run request and return single span. */
  private def runRequestSingleSpan(
      endpoints: List[ServerEndpoint[Any, Task]],
      request: ServerRequest,
      config: ZIOpenTelemetryTracingConfig = ZIOpenTelemetryTracingConfig()
  ): ZIO[Tracing with InMemorySpanExporter, Throwable, SpanData] =
    runRequest(endpoints, request, config).map(_.get(0))

  private def runRequestStreamSingleSpan(
      endpoints: List[ServerEndpoint[ZioStreams, Task]],
      request: ServerRequest,
      config: ZIOpenTelemetryTracingConfig = ZIOpenTelemetryTracingConfig()
  ): ZIO[Tracing with InMemorySpanExporter, Throwable, SpanData] =
    runRequestStream(endpoints, request, config).map(_.get(0))

  // Tests are provided with layers at the suite level via .provide()

  // ─── Span Creation & Naming ────────────────────────────────────────────────

  private val spanNamingSuite = suite("Span Creation & Naming")(
    test("report a simple trace") {
      val ep = endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(uri"http://example.com/person?name=Adam")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getName)(equalTo("GET /person")) &&
        assert(span.getKind)(equalTo(SpanKind.SERVER)) &&
        assert(span.getAttributes.size())(equalTo(6))
      }
    },
    test("use path template with path parameters as span name") {
      val ep = endpoint
        .in("person" / path[String]("name") / path[String]("surname") / "info")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(uri"http://example.com/person/Adam/Smith/info")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getName)(equalTo("GET /person/{name}/{surname}/info")) &&
        assert(span.getAttributes.get(HttpAttributes.HTTP_ROUTE))(equalTo("/person/{name}/{surname}/info"))
      }
    },
    test("use POST method in span name") {
      val ep = endpoint.post
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("created")))

      val request = serverRequestFromUri(uri"http://example.com/person?name=Adam", _method = Method.POST)
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getName)(equalTo("POST /person")) &&
        assert(span.getAttributes.get(HttpAttributes.HTTP_REQUEST_METHOD))(equalTo("POST"))
      }
    },
    test("use complex path template with mixed segments") {
      val ep = endpoint
        .in("api" / "v2" / "users" / path[Int]("id") / "profile")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("profile")))

      val request = serverRequestFromUri(uri"http://example.com/api/v2/users/123/profile")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getName)(equalTo("GET /api/v2/users/{id}/profile"))
      }
    }
  )

  // ─── Request Attributes ────────────────────────────────────────────────────

  private val requestAttributesSuite = suite("Request Attributes")(
    test("default request attributes include method, path, scheme, host") {
      val ep = endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(
        uri"http://example.com/person?name=Adam",
        _headers = List(Header(HeaderNames.Host, "example.com"))
      )
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        val attrs = span.getAttributes
        assert(attrs.get(HttpAttributes.HTTP_REQUEST_METHOD))(equalTo("GET")) &&
        assert(attrs.get(UrlAttributes.URL_PATH))(equalTo("/person")) &&
        assert(attrs.get(UrlAttributes.URL_SCHEME))(equalTo("http")) &&
        assert(attrs.get(ServerAttributes.SERVER_ADDRESS))(equalTo("example.com"))
      }
    },
    test("extract host from Forwarded header") {
      val ep = endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(
        uri"http://example.com/person?name=Adam",
        _headers = List(Header(HeaderNames.Forwarded, Forwarded(None, None, Some("softwaremill.com"), None).toString))
      )
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getAttributes.get(ServerAttributes.SERVER_ADDRESS))(equalTo("softwaremill.com"))
      }
    },
    test("extract host from X-Forwarded-Host header") {
      val ep = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(
        uri"http://example.com/hello",
        _headers = List(Header(HeaderNames.XForwardedHost, "proxy.example.com"))
      )
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getAttributes.get(ServerAttributes.SERVER_ADDRESS))(equalTo("proxy.example.com"))
      }
    },
    test("extract host from :authority pseudo-header") {
      val ep = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(
        uri"http://example.com/hello",
        _headers = List(Header(":authority", "authority.example.com"))
      )
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getAttributes.get(ServerAttributes.SERVER_ADDRESS))(equalTo("authority.example.com"))
      }
    },
    test("extract host from Host header") {
      val ep = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(
        uri"http://example.com/hello",
        _headers = List(Header(HeaderNames.Host, "host.example.com"))
      )
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getAttributes.get(ServerAttributes.SERVER_ADDRESS))(equalTo("host.example.com"))
      }
    },
    test("fallback to 'unknown' when no host headers present") {
      val ep = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      // serverRequestFromUri with empty headers and a URI without explicit Host header
      val request = serverRequestFromUri(uri"http://example.com/hello")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        // When there's no Host/Forwarded/X-Forwarded-Host/:authority header, defaults to "unknown"
        assert(span.getAttributes.get(ServerAttributes.SERVER_ADDRESS))(equalTo("unknown"))
      }
    },
    test("detect HTTPS scheme") {
      val ep = endpoint
        .in("secure")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("secure")))

      val request = serverRequestFromUri(uri"https://example.com/secure")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getAttributes.get(UrlAttributes.URL_SCHEME))(equalTo("https"))
      }
    }
  )

  // ─── Response Attributes ───────────────────────────────────────────────────

  private val responseAttributesSuite = suite("Response Attributes")(
    test("200 OK response sets status code attribute") {
      val ep = endpoint
        .in("person")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(uri"http://example.com/person")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getAttributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE))(equalTo(java.lang.Long.valueOf(200L)))
      }
    },
    test("404 error response sets status code attribute") {
      val ep = endpoint
        .in("person")
        .out(stringBody)
        .errorOut(statusCode(sttp.model.StatusCode.NotFound))
        .serverLogic[Task](_ => ZIO.succeed(Left(())))

      val request = serverRequestFromUri(uri"http://example.com/person")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getAttributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE))(equalTo(java.lang.Long.valueOf(404L)))
      }
    }
  )

  // ─── Error Handling ────────────────────────────────────────────────────────

  private val errorHandlingSuite = suite("Error Handling")(
    test("5xx server error sets span error status") {
      val ep = endpoint
        .in("fail")
        .out(stringBody)
        .errorOut(statusCode(sttp.model.StatusCode.InternalServerError))
        .serverLogic[Task](_ => ZIO.succeed(Left(())))

      val request = serverRequestFromUri(uri"http://example.com/fail")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getStatus.getStatusCode)(equalTo(OtelStatusCode.ERROR)) &&
        assert(span.getAttributes.get(ErrorAttributes.ERROR_TYPE))(equalTo("500"))
      }
    },
    test("exception in endpoint logic sets span error status") {
      val ep = endpoint
        .in("crash")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.fail(new RuntimeException("boom")))

      val request = serverRequestFromUri(uri"http://example.com/crash")
      for {
        _ <- runRequest(List(ep), request).either
        exported <- ZIO.service[InMemorySpanExporter]
        finishedSpans = exported.getFinishedSpanItems()
      } yield {
        // The span should be recorded even if there was an exception
        assertTrue(finishedSpans.size() >= 1) &&
        assert(finishedSpans.get(0).getStatus.getStatusCode)(equalTo(OtelStatusCode.ERROR)) &&
        assert(finishedSpans.get(0).getAttributes.get(ErrorAttributes.ERROR_TYPE))(equalTo("RuntimeException"))
      }
    },
    test("4xx client error does *not* set span error status") {
      val ep = endpoint
        .in("client-error")
        .out(stringBody)
        .errorOut(statusCode(sttp.model.StatusCode.BadRequest))
        .serverLogic[Task](_ => ZIO.succeed(Left(())))

      val request = serverRequestFromUri(uri"http://example.com/client-error")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        // 4xx is not a server error, so span status should NOT be ERROR
        assert(span.getStatus.getStatusCode)(not(equalTo(OtelStatusCode.ERROR))) &&
        assert(span.getAttributes.get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE))(equalTo(java.lang.Long.valueOf(400L)))
      }
    }
  )

  // ─── Context Propagation ───────────────────────────────────────────────────

  private val contextPropagationSuite = suite("Context Propagation")(
    test("extract trace context from traceparent header") {
      val ep = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(
        uri"http://example.com/hello",
        _headers = List(Header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
      )
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        assert(span.getSpanContext.getTraceId)(equalTo("4bf92f3577b34da6a3ce929d0e0e4736")) &&
        assert(span.getParentSpanContext.getSpanId)(equalTo("00f067aa0ba902b7")) &&
        assertTrue(span.getParentSpanContext.isRemote)
      }
    },
    test("create fresh trace when no traceparent header is present") {
      val ep = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(uri"http://example.com/hello")
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        // Should have a valid trace ID but no valid parent span context
        assertTrue(span.getSpanContext.isValid) &&
        assertTrue(!span.getParentSpanContext.isValid)
      }
    },
    test("malformed traceparent starts a new trace gracefully") {
      val ep = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(
        uri"http://example.com/hello",
        _headers = List(Header("traceparent", "invalid-traceparent-value"))
      )
      for {
        span <- runRequestSingleSpan(List(ep), request)
      } yield {
        // Should gracefully start a new trace
        assertTrue(span.getSpanContext.isValid) &&
        assertTrue(!span.getParentSpanContext.isValid)
      }
    },
    test("child span can be created with correct parent context") {
      def ep(tracing: Tracing) = endpoint
        .in("hello")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task] { _ =>
          tracing.span("child-span")(ZIO.succeed(Right("hello")))
        }

      val request = serverRequestFromUri(
        uri"http://example.com/hello",
        _headers = List(Header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
      )
      for {
        tracing <- ZIO.service[Tracing]
        spans <- runRequest(List(ep(tracing)), request)
        parentSpan <- ZIO
          .fromOption(spans.asScala.find(_.getName == "GET /hello"))
          .mapError(_ => new RuntimeException("Parent span not found"))
          .orDie
        childSpan <- ZIO
          .fromOption(spans.asScala.find(_.getName == "child-span"))
          .mapError(_ => new RuntimeException("Child span not found"))
          .orDie

      } yield {
        // Should have two spans: the server span and the child span, with correct parent-child relationship
        assertTrue(spans.size() >= 2) &&
        assertTrue(childSpan.getParentSpanContext.getSpanId == parentSpan.getSpanContext.getSpanId)
      }
    }.provide(tracingMockLayer(), OpenTelemetry.contextZIO)
  )

  // ─── Endpoint Matching Behavior ────────────────────────────────────────────

  private val endpointMatchingSuite = suite("Endpoint Matching Behavior")(
    test("unmatched request does not set error status") {
      val ep = endpoint
        .in("person")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      // Request to a path that doesn't match any endpoint
      val request = serverRequestFromUri(uri"http://example.com/unknown-path")
      for {
        spans <- runRequest(List(ep), request)
      } yield {
        assertTrue(spans.size() == 1) &&
        assert(spans.get(0).getStatus.getStatusCode)(not(equalTo(OtelStatusCode.ERROR)))
      }
    },
    test("decode failure on matched endpoint - span is created with initial name") {
      val ep = endpoint
        .in("person")
        .in(query[Int]("age")) // expects Int, will fail with String
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      // Send "notanint" for the Int query parameter - decode will fail
      val request = serverRequestFromUri(uri"http://example.com/person?age=notanint")
      for {
        spans <- runRequest(List(ep), request)
      } yield {
        // A span is still created even when decode fails;
        // without a DecodeFailureHandler interceptor producing a response,
        // the endpoint is not confirmed, so span retains the initial request-based name.
        assertTrue(spans.size() >= 1) &&
        assertTrue(spans.get(0).getKind == SpanKind.SERVER) &&
        assert(spans.get(0).getStatus.getStatusCode)(equalTo(OtelStatusCode.ERROR))
      }
    },
    test("security failure on matched endpoint updates span name") {
      val ep = endpoint
        .securityIn("secure")
        .securityIn(header[String]("X-Auth-Token"))
        .in("data")
        .out(stringBody)
        .errorOut(stringBody)
        .serverSecurityLogic[String, Task](token =>
          if (token == "valid") ZIO.succeed(Right("principal"))
          else ZIO.succeed(Left("unauthorized"))
        )
        .serverLogic(_ => _ => ZIO.succeed(Right("data")))

      val request = serverRequestFromUri(
        uri"http://example.com/secure/data",
        _headers = List(Header("X-Auth-Token", "invalid"))
      )
      for {
        spans <- runRequest(List(ep), request)
      } yield {
        assertTrue(spans.size() >= 1) &&
        assert(spans.get(0).getName)(equalTo("GET /secure/data"))
      }
    }
  )

  // ─── Configuration Customization ──────────────────────────────────────────

  private val configCustomizationSuite = suite("Configuration Customization")(
    test("custom spanNameFromEndpointAndAttributes overrides default naming") {
      val customConfig = ZIOpenTelemetryTracingConfig(
        spanNameFromEndpointAndAttributes = (request, _) => (s"custom-${request.method.method}", Attributes.empty())
      )

      val ep = endpoint
        .in("person")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(uri"http://example.com/person")
      for {
        span <- runRequestSingleSpan(List(ep), request, customConfig)
      } yield {
        assert(span.getName)(equalTo("custom-GET"))
      }
    },
    test("custom requestAttributes adds custom attributes to span") {
      val customConfig = ZIOpenTelemetryTracingConfig(
        requestAttributes = request =>
          Attributes
            .builder()
            .put(HttpAttributes.HTTP_REQUEST_METHOD, request.method.method)
            .put("custom.attribute", "custom-value")
            .build()
      )

      val ep = endpoint
        .in("person")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(uri"http://example.com/person")
      for {
        span <- runRequestSingleSpan(List(ep), request, customConfig)
      } yield {
        import io.opentelemetry.api.common.AttributeKey
        val customAttr = span.getAttributes.get(AttributeKey.stringKey("custom.attribute"))
        assert(customAttr)(equalTo("custom-value"))
      }
    },
    test("custom responseAttributes adds extra response attributes") {
      import io.opentelemetry.api.common.AttributeKey
      val customConfig = ZIOpenTelemetryTracingConfig(
        responseAttributes = (_, response) =>
          Attributes.of(
            HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
            java.lang.Long.valueOf(response.code.code.toLong),
            AttributeKey.stringKey("custom.response"),
            "response-value"
          )
      )

      val ep = endpoint
        .in("person")
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](_ => ZIO.succeed(Right("hello")))

      val request = serverRequestFromUri(uri"http://example.com/person")
      for {
        span <- runRequestSingleSpan(List(ep), request, customConfig)
      } yield {
        import io.opentelemetry.api.common.AttributeKey
        val customAttr = span.getAttributes.get(AttributeKey.stringKey("custom.response"))
        assert(customAttr)(equalTo("response-value"))
      }
    },
    test("custom errorAttributes for server error") {
      val customConfig = ZIOpenTelemetryTracingConfig(
        errorAttributes = {
          case Left(statusCode) =>
            Attributes
              .builder()
              .put(ErrorAttributes.ERROR_TYPE, s"custom-${statusCode.code}")
              .build()
          case Right(exception) =>
            Attributes
              .builder()
              .put(ErrorAttributes.ERROR_TYPE, s"custom-${exception.getClass.getSimpleName}")
              .build()
        }
      )

      val ep = endpoint
        .in("fail")
        .out(stringBody)
        .errorOut(statusCode(sttp.model.StatusCode.InternalServerError))
        .serverLogic[Task](_ => ZIO.succeed(Left(())))

      val request = serverRequestFromUri(uri"http://example.com/fail")
      for {
        span <- runRequestSingleSpan(List(ep), request, customConfig)
      } yield {
        assert(span.getAttributes.get(ErrorAttributes.ERROR_TYPE))(equalTo("custom-500"))
      }
    }
  )

  // ─── Streaming ────────────────────────────────────────────────────────────

  private val streamingSuite = suite("Streaming")(
    test("streaming endpoint produces a span") {
      val ep: ServerEndpoint[ZioStreams, Task] = endpoint
        .in("stream")
        .out(streamTextBody(ZioStreams)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
        .serverLogicPure[Task](_ => Right(ZStream.fromIterable("abc".getBytes(StandardCharsets.UTF_8))))

      val request = serverRequestFromUri(uri"http://example.com/stream")
      for {
        _ <- ZIO.logDebug("Running request")
        span <- runRequestStreamSingleSpan(List(ep), request)
      } yield {
        assert(span.getName)(equalTo("GET /stream")) &&
        assert(span.getKind)(equalTo(SpanKind.SERVER))
      }
    },
    test("streaming error produces a span flagged as error") {
      val ep: ServerEndpoint[ZioStreams, Task] = endpoint
        .in("stream")
        .out(streamTextBody(ZioStreams)(CodecFormat.TextPlain(), Some(StandardCharsets.UTF_8)))
        .zServerLogic(_ => ZIO.succeed(ZStream.fromIterable("abc".getBytes(StandardCharsets.UTF_8)).flatMap(_ => ZStream.fail(new RuntimeException("boom")))))

      val request = serverRequestFromUri(uri"http://example.com/stream")
      for {
        _ <- ZIO.logDebug("Running request")
        span <- runRequestStreamSingleSpan(List(ep), request)
        _ <- ZIO.logDebug(s"Span: $span")
      } yield {
        assert(span.getName)(equalTo("GET /stream")) &&
        assert(span.getKind)(equalTo(SpanKind.SERVER)) &&
        // the failure occurs while the response body is being streamed; the span must reflect it
        assert(span.getStatus.getStatusCode)(equalTo(OtelStatusCode.ERROR))
      }

    }
  )

  // ─── Concurrency ──────────────────────────────────────────────────────────

  private val concurrencySuite = suite("Concurrency")(
    test("concurrent requests produce isolated spans with distinct trace IDs") {
      val ep = endpoint
        .in("person")
        .in(query[String]("name"))
        .out(stringBody)
        .errorOut(stringBody)
        .serverLogic[Task](name => ZIO.succeed(Right(s"hello $name")))

      for {
        tracing <- ZIO.service[Tracing]
        exported <- ZIO.service[InMemorySpanExporter]
        _ <- ZIO.succeed(exported.reset())
        interpreter = new ServerInterpreter[Any, Task, String, NoStreams](
          _ => List(ep),
          ZIOTestRequestBody,
          StringToResponseBody,
          List(ZIOpenTelemetryTracing(tracing)),
          _ => ZIO.succeed(())
        )
        names = (1 to 20).map(i => s"user$i").toList
        _ <- ZIO.foreachPar(names) { name =>
          val request = serverRequestFromUri(Uri.unsafeParse(s"http://example.com/person?name=$name"))
          interpreter(request)
        }
        spans = exported.getFinishedSpanItems().asScala.toList
        traceIds = spans.map(_.getSpanContext.getTraceId).toSet
      } yield {
        // Each request should produce its own span
        assertTrue(spans.size == 20) &&
        // All trace IDs should be distinct (each is an independent trace)
        assertTrue(traceIds.size == 20) &&
        // All spans should be SERVER spans
        assertTrue(spans.forall(_.getKind == SpanKind.SERVER)) &&
        // All spans should have the matched endpoint name
        assertTrue(spans.forall(_.getName == "GET /person"))
      }
    }
  )

  // ─── Main Spec ─────────────────────────────────────────────────────────────

  def spec: Spec[Any, Throwable] =
    suite("zio opentelemetry tapir interceptor")(
      spanNamingSuite,
      requestAttributesSuite,
      responseAttributesSuite,
      errorHandlingSuite,
      contextPropagationSuite,
      endpointMatchingSuite,
      configCustomizationSuite,
      streamingSuite,
      concurrencySuite
    ).provide(
      OpenTelemetry.contextZIO,
      tracingMockLayer(false)
    ) @@ TestAspect.sequential
}

object ZIOTestRequestBody extends RequestBody[Task, NoStreams] {
  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Task[RawValue[R]] = ???
  override val streams: Streams[NoStreams] = NoStreams
  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
}
object ZIOTestRequestStreamBody extends RequestBody[Task, ZioStreams] {
  override def toRaw[R](serverRequest: ServerRequest, bodyType: RawBodyType[R], maxBytes: Option[Long]): Task[RawValue[R]] = ???
  override val streams: Streams[ZioStreams] = ZioStreams
  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream = ???
}
