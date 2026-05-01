package sttp.tapir.server.o11y.otel4z

import io.opentelemetry.api.common.Attributes

import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.`export`.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import zio._

import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter

object LoggerProvider extends OtlpEndpoint {

  /** gRPC exporter that sends logs to the endpoint specified in the environment variable.
    *
    * If the environment variable is not set, will fallback OTEL_EXPORTER_OTLP_ENDPOINT env var or "http://localhost:4317".
    */
  def grpc(resourceName: String): URIO[Scope, Option[SdkLoggerProvider]] =
    for {
      logRecordExporter <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            OtlpGrpcLogRecordExporter
              .builder()
              .setEndpoint(getEndpoint("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT"))
              .build()
          )
        )
      logRecordProcessor <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(SimpleLogRecordProcessor.create(logRecordExporter))
        )
      loggerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkLoggerProvider
              .builder()
              .setResource(
                Resource.create(
                  Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .addLogRecordProcessor(logRecordProcessor)
              .build()
          )
        )
    } yield Some(loggerProvider)

}
