package sttp.tapir.server.tracing.ziotel

import io.opentelemetry.api.common.Attributes

import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.`export`.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import zio._

import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter

object LoggerProvider {

  /** Prints to stdout in OTLP gRPC format
    */
  def grpc(resourceName: String): RIO[Scope, SdkLoggerProvider] =
    for {
      logRecordExporter <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            OtlpGrpcLogRecordExporter
              .builder()
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
    } yield loggerProvider

}
