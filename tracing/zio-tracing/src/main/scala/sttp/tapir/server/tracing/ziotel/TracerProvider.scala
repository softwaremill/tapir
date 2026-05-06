package sttp.tapir.server.tracing.ziotel

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.semconv.ServiceAttributes
import zio._
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter

object TracerProvider {

  /** Prints to stdout in OTLP Json format
    */
  def stdout(resourceName: String): RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter <-
        ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingSpanExporter.create()))
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
                  Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider

  /** https://www.jaegertracing.io/
    */
  def grpc(resourceName: String): RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(OtlpGrpcSpanExporter.builder().build())
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
                  Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider

  /** https://fluentbit.io/
    */
  def fluentbit(resourceName: String): RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(OtlpHttpSpanExporter.builder().build())
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
                  Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)
                )
              )
              .addSpanProcessor(spanProcessor)
              .build()
          )
        )
    } yield tracerProvider

}
