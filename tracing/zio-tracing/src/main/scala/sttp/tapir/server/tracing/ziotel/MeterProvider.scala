package sttp.tapir.server.tracing.ziotel

import zio._
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter

object MeterProvider {

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

  def grpc(resourceName: String): RIO[Scope, SdkMeterProvider] =
    for {
      metricExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(OtlpGrpcMetricExporter.builder().build())
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

}
