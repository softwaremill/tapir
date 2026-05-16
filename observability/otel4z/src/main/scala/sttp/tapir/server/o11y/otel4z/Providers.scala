package sttp.tapir.server.o11y.otel4z

import zio._

import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.api
import sttp.tapir.server.metrics.opentelemetry.OpenTelemetryMetrics
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage

/**
  * Logging, Metrics and Tracing providers for OpenTelemetry.
  */
trait Logging {
  this: ZIOpentelemetryBase =>

  /**
   * Provides a logger provider for OpenTelemetry, which logs in OTLP Json format to stdout.
   */ 
  override def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = LoggerProvider.grpc(resourceName)

  /**
    * A OpenTelemetry logging layer, with configurable instrumentation scope name and log level.
    *
    * @param instrumentationScopeName
    * @param logLevel
    * @return
    */
  def otel4zLogging(instrumentationScopeName: String, logLevel: LogLevel = LogLevel.Info): URLayer[api.OpenTelemetry with ContextStorage, Unit] = OpenTelemetry.logging(
    instrumentationScopeName = instrumentationScopeName,
    logLevel = logLevel
  )
}

/**
  * Metrics provider for OpenTelemetry.
  */
trait Metrics {
  this: ZIOpentelemetryBase =>

  /**
   * Provides a meter provider for OpenTelemetry, which exports metrics in OTLP Json format to stdout.
   */
  override def meterProvider: URIO[Scope, Option[SdkMeterProvider]] = MeterProvider.grpc(resourceName)

  /**
    * A OpenTelemetry runtime metrics layer.
    *
    * @return
    */
  def otel4zRuntimeTelemetry = ZLayer.fromZIO(
    for {
      openTelemetry <- ZIO.service[io.opentelemetry.api.OpenTelemetry]
      _ <- ZIO.fromAutoCloseable(ZIO.succeed(RuntimeTelemetry.create(openTelemetry)))
    } yield ()
  )

  /**
    * A OpenTelemetry metrics layer, with configurable instrumentation scope name, version and schema url.
    *
    * @param instrumentationScopeName
    * @param instrumentationVersion
    * @param schemaUrl
    * @param logAnnotated
    * @return
    */
  def otel4zMetrics(
      instrumentationScopeName: String,
      instrumentationVersion: Option[String] = None,
      schemaUrl: Option[String] = None,
      logAnnotated: Boolean = false
  ) = OpenTelemetry.metrics(
    instrumentationScopeName = instrumentationScopeName,
    instrumentationVersion = instrumentationVersion,
    schemaUrl = schemaUrl,
    logAnnotated = logAnnotated
  )

  /**
    * A OpenTelemetry metrics interceptor for tapir, with configurable instrumentation scope name.
    * 
    * It uses the OpenTelemetry instance from the environment, which is provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
    *
    * @param instrumentationScopeName
    * @param otel
    * @return
    */
  def otel4zMetricsInterceptor(instrumentationScopeName: String = "tapir")(implicit otel: api.OpenTelemetry): MetricsRequestInterceptor[Task] = {
    val meter: api.metrics.Meter = otel.meterBuilder(instrumentationScopeName).build()

    val metrics = OpenTelemetryMetrics.default[Task](meter)

    metrics.metricsInterceptor()
  }

}


trait Traces {
  this: ZIOpentelemetryBase =>

  override def tracerProvider: URIO[Scope, Option[SdkTracerProvider]] = TracerProvider.grpc(resourceName)

  def otel4zTracing(
      instrumentationScopeName: String,
      instrumentationVersion: Option[String] = None,
      schemaUrl: Option[String] = None,
      logAnnotated: Boolean = false
  ) = OpenTelemetry.tracing(
    instrumentationScopeName = instrumentationScopeName,
    instrumentationVersion = instrumentationVersion,
    schemaUrl = schemaUrl,
    logAnnotated = logAnnotated
  )
}

