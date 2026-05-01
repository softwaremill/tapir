package sttp.tapir.server.o11y.otel4z

import zio._
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter

object MeterProvider extends OtlpEndpoint {

  /** Prints to stdout in OTLP Json format
    */
  def stdout(resourceName: String): RIO[Scope, SdkMeterProvider] =
    for {
      metricExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(OtlpJsonLoggingMetricExporter.create())
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
                  Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .build()
          )
        )
    } yield meterProvider

  /** gRPC exporter that sends metrics to the endpoint specified in the environment variable.
    *
    * If the environment variable is not set, will fallback OTEL_EXPORTER_OTLP_ENDPOINT env var or "http://localhost:4317".
    */
  def grpc(resourceName: String): URIO[Scope, Option[SdkMeterProvider]] =
    for {
      metricExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(OtlpGrpcMetricExporter.builder().setEndpoint(getEndpoint("OTEL_EXPORTER_OTLP_METRICS_ENDPOINT")).build())
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
                  Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .build()
          )
        )
    } yield Some(meterProvider)

}
