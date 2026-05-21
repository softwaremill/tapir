package sttp.tapir.server.o11y.ziopentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import zio._

/** Provides a tracer provider for OpenTelemetry.
  */
object TracerProvider {

  /** Provides a tracer provider for OpenTelemetry, which logs in OTLP Json format as gRPC if either of the following environment variables
    * is set:
    *   - `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`
    *   - `OTEL_EXPORTER_OTLP_ENDPOINT`
    */
  def grpc(attributes: Attributes): URIO[Scope, Option[SdkTracerProvider]] =
    OtlpEndpoint("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT") match {

      case None =>
        ZIO.logInfo(
          "No OTLP traces endpoint configured, skipping OpenTelemetry tracing setup. To enable it, set either OTEL_EXPORTER_OTLP_TRACES_ENDPOINT or OTEL_EXPORTER_OTLP_ENDPOINT environment variable."
        ) *> ZIO.succeed(None)
      case Some(endpoint) =>
        for {
          _ <- ZIO.logInfo(s"Configuring OpenTelemetry tracing to $endpoint")
          spanExporter <- ZIO.fromAutoCloseable(
            ZIO.succeed(OtlpGrpcSpanExporter.builder().setEndpoint(endpoint).build())
          )
          spanProcessor <- ZIO.fromAutoCloseable(
            ZIO.succeed(SimpleSpanProcessor.create(spanExporter))
          )
          tracerProvider <-
            ZIO.fromAutoCloseable(
              ZIO.succeed(
                SdkTracerProvider
                  .builder()
                  .setResource(
                    Resource.create(
                      attributes
                    )
                  )
                  .addSpanProcessor(spanProcessor)
                  .build()
              )
            )
        } yield Some(tracerProvider)

    }
}
