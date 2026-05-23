package sttp.tapir.server.o11y.ziopentelemetry

import zio._

import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.api
import sttp.tapir.server.metrics.opentelemetry.OpenTelemetryMetrics
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import io.opentelemetry.sdk.logs.SdkLoggerProvider

import zio.telemetry.opentelemetry.context.ContextStorage

/** Logging provider for OpenTelemetry.
  *
  * The providers are configured to export logs in OTLP gRPC format to collector.
  *
  * The providers are used by the OpenTelemetry layers, which are/must be provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
  */
trait Logging {
  this: ZIOpenTelemetryBase =>

  /** Provides a logger provider for OpenTelemetry, which logs in OTLP gRPC format with [[LoggerProvider]]
    */
  override def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = LoggerProvider.grpc(attributes)

  /** A OpenTelemetry logging layer, with configurable instrumentation scope name and log level.
    *
    * @param instrumentationScopeName
    * @param logLevel
    * @return
    */
  def otel4zLogging(
      instrumentationScopeName: String,
      logLevel: LogLevel = LogLevel.Info
  ): URLayer[api.OpenTelemetry with ContextStorage, Unit] = OpenTelemetry.logging(
    instrumentationScopeName = instrumentationScopeName,
    logLevel = logLevel
  )
}

/** Metrics providers for OpenTelemetry.
  *
  * The providers are configured to export metricsin OTLP gRPC format to collector.
  *
  * The providers are used by the OpenTelemetry layers, which are/must be provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
  */
trait Metrics {
  this: ZIOpenTelemetryBase =>

  /** Provides a meter provider for OpenTelemetry, which exports metrics in OTLP gRPC format with [[MeterProvider]]
    */
  override def meterProvider: URIO[Scope, Option[SdkMeterProvider]] = MeterProvider.grpc(attributes)

  /** A OpenTelemetry metrics layer, with configurable instrumentation scope name, version and schema url.
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

  /** A OpenTelemetry metrics interceptor for tapir, with configurable instrumentation scope name.
    *
    * It uses the OpenTelemetry instance from the environment, which is provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
    *
    * @param instrumentationScopeName
    * @param otel
    * @return
    */
  def otel4zMetricsInterceptor(
      instrumentationScopeName: String = "tapir"
  )(implicit otel: api.OpenTelemetry): MetricsRequestInterceptor[Task] = {
    val meter: api.metrics.Meter = otel.meterBuilder(instrumentationScopeName).build()

    val metrics = OpenTelemetryMetrics.default[Task](meter)

    metrics.metricsInterceptor()
  }

}

/** Tracing providers for OpenTelemetry.
  *
  * The providers are configured to export traces in OTLP gRPC format to collector.
  *
  * The providers are used by the OpenTelemetry layers, which are/must be provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
  */
trait Traces {
  this: ZIOpenTelemetryBase =>

  /** Provides a tracer provider for OpenTelemetry, which exports traces in OTLP gRPC format with [[TracerProvider]]
    */
  override def tracerProvider: URIO[Scope, Option[SdkTracerProvider]] = TracerProvider.grpc(attributes)

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
