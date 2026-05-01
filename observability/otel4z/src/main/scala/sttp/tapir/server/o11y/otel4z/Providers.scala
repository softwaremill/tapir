package sttp.tapir.server.o11y.otel4z

import zio._


import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.api
import sttp.tapir.server.metrics.opentelemetry.OpenTelemetryMetrics
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import io.opentelemetry.sdk.logs.SdkLoggerProvider


trait Logging {
  this: ZIOtelBase =>

  override def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = LoggerProvider.grpc(resourceName)

  def otel4zLogging( instrumentationScopeName: String,
    logLevel: LogLevel = LogLevel.Info) = OpenTelemetry.logging(
      instrumentationScopeName = instrumentationScopeName,
      logLevel = logLevel
    )
}

trait Metrics {
    this: ZIOtelBase =>

    
    override def meterProvider: URIO[Scope, Option[SdkMeterProvider]] =  MeterProvider.grpc(resourceName)

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

    def metricsInterceptor(using otel: api.OpenTelemetry): MetricsRequestInterceptor[Task] = {
      val meter: api.metrics.Meter = otel.meterBuilder("tapir").build()

      val metrics = OpenTelemetryMetrics.default[Task](meter)

      metrics.metricsInterceptor()
    }

}

object Metrics {
 def live(instrumentName : String) = OpenTelemetry.metrics(instrumentName)
}

trait Traces {
    this: ZIOtelBase =>

    override def tracerProvider: URIO[Scope, Option[SdkTracerProvider]]  = TracerProvider.grpc(resourceName)

    def otel4zTracing(instrumentationScopeName: String,
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

object Traces {
 def live(instrumentName : String) = OpenTelemetry.tracing(instrumentName)

}