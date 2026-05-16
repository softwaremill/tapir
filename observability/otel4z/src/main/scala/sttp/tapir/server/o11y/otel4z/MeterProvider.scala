package sttp.tapir.server.o11y.otel4z

import zio._
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.api.common.Attributes

import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter

/** Provides a meter provider for OpenTelemetry.
  */
object MeterProvider {

  /** Provides a meter provider for OpenTelemetry, which logs in OTLP Json format as gRPC if either of the following environment variables
    * is set:
    *   - `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`
    *   - `OTEL_EXPORTER_OTLP_ENDPOINT`
    */
  def grpc(attributes: Attributes): URIO[Scope, Option[SdkMeterProvider]] = OtlpEndpoint("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT") match {
    case None =>
      ZIO.logInfo(
        "No OTLP metrics endpoint configured, skipping OpenTelemetry metrics setup. To enable it, set either OTEL_EXPORTER_OTLP_METRICS_ENDPOINT or OTEL_EXPORTER_OTLP_ENDPOINT environment variable."
      ) *> ZIO.succeed(None)

    case Some(endpoint) =>
      for {
        _ <- ZIO.logInfo(s"Configuring OpenTelemetry metrics to $endpoint")
        metricExporter <- ZIO.fromAutoCloseable(
          ZIO.succeed(OtlpGrpcMetricExporter.builder().setEndpoint(endpoint).build())
        )
        metricReader <-
          ZIO.fromAutoCloseable(
            ZIO.succeed(
              PeriodicMetricReader
                .builder(metricExporter)
                .setInterval(5.second)
                .build()
            )
          )
        meterProvider <-
          ZIO.fromAutoCloseable(
            ZIO.succeed(
              SdkMeterProvider
                .builder()
                .registerMetricReader(metricReader)
                .setResource(
                  Resource.create(
                    attributes
                  )
                )
                .build()
            )
          )
      } yield Some(meterProvider)

  }
}
