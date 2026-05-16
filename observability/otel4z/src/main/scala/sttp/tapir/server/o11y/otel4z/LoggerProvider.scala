package sttp.tapir.server.o11y.otel4z

import io.opentelemetry.api.common.Attributes

import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.`export`.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import zio._

import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter

/** Provides a logger provider for OpenTelemetry.
  */
object LoggerProvider {

  /** Provides a logger provider for OpenTelemetry, which logs in OTLP Json format as gRPC if either of the following environment variables
    * is set:
    *   - `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT`
    *   - `OTEL_EXPORTER_OTLP_ENDPOINT`
    */
  def grpc(attributes: Attributes): URIO[Scope, Option[SdkLoggerProvider]] = OtlpEndpoint("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT") match {
    case None =>
      ZIO.logInfo(
        "No OTLP logs endpoint configured, skipping OpenTelemetry logging setup. To enable it, set either OTEL_EXPORTER_OTLP_LOGS_ENDPOINT or OTEL_EXPORTER_OTLP_ENDPOINT environment variable."
      ) *> ZIO.succeed(None)

    case Some(endpoint) =>
      for {
        _ <- ZIO.logInfo(s"Configuring OpenTelemetry logging to $endpoint")
        logRecordExporter <-
          ZIO.fromAutoCloseable(
            ZIO.succeed(
              OtlpGrpcLogRecordExporter
                .builder()
                .setEndpoint(endpoint)
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
                    attributes
                  )
                )
                .addLogRecordProcessor(logRecordProcessor)
                .build()
            )
          )
      } yield Some(loggerProvider)

  }
}
