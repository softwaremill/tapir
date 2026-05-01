package sttp.tapir.server.ziopentelemetry

import zio._
import io.opentelemetry.sdk.trace.SdkTracerProvider
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.resources.Resource
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessorBuilder
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder

trait Traces {
  this: ZIOpenTelemetryApp =>

  def traceEndpoint: ZIO[Any, Nothing, Option[String]] = OtlpEndpoint("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT") match {
    case None =>
      ZIO.logInfo(
        "No OTLP traces endpoint configured, skipping OpenTelemetry tracing setup. To enable it, set either OTEL_EXPORTER_OTLP_TRACES_ENDPOINT or OTEL_EXPORTER_OTLP_ENDPOINT environment variable."
      ) *> ZIO.succeed(None)

    case Some(endpoint) =>
      ZIO.some(endpoint)
  }

  /** Provides a tracer provider for OpenTelemetry, which logs in OTLP Json format as gRPC if either of the following environment variables
    * is set:
    *   - `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`
    *   - `OTEL_EXPORTER_OTLP_ENDPOINT`
    */
  override def tracerProvider: URIO[Scope, Option[SdkTracerProvider]] = for {

    endpointOption <- traceEndpoint

    endpoint <- endpointOption match {
      case Some(endpoint) => provider(endpoint).map(Some(_))
      case None           => ZIO.none
    }

  } yield endpoint

  /** Customize the span exporter builder.
    *
    * By default, it will use the endpoint from the environment variable.
    */
  def customizeSpanExporter(builder: OtlpGrpcSpanExporterBuilder): OtlpGrpcSpanExporterBuilder = builder

  /** Creates a span exporter for the given endpoint. It can be customized by overriding the `customizeSpanExporter` method.
    */
  private def spanExporter(endpoint: String): OtlpGrpcSpanExporter =
    customizeSpanExporter(
      OtlpGrpcSpanExporter
        .builder()
        .setEndpoint(endpoint)
    )
      .build()

  def customizeSpanProcessor(spanProcessorBuilder: SimpleSpanProcessorBuilder): SimpleSpanProcessorBuilder = spanProcessorBuilder

  private def spanProcessor(spanExporter: OtlpGrpcSpanExporter): SpanProcessor =
    customizeSpanProcessor(
      SimpleSpanProcessor.builder(spanExporter)
    )
      .build()

  /** Customize the SDK tracer provider builder.
    *
    * By default, it will use the resource from the environment variable.
    */
  def customizeSdkTraceProvider(builder: SdkTracerProviderBuilder): SdkTracerProviderBuilder = builder

  private def sdkTracerProvider(spanProcessor: SpanProcessor): SdkTracerProvider =
    customizeSdkTraceProvider(
      SdkTracerProvider
        .builder()
        .setResource(
          Resource.create(
            attributes
          )
        )
        .addSpanProcessor(spanProcessor)
    )
      .build()

  private def provider(endpoint: String): URIO[Scope, SdkTracerProvider] =

    for {
      _ <- ZIO.logInfo(s"Configuring OpenTelemetry tracing to $endpoint")
      spanExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(spanExporter(endpoint))
      )
      spanProcessor <- ZIO.fromAutoCloseable(
        ZIO.succeed(spanProcessor(spanExporter))
      )
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            sdkTracerProvider(spanProcessor)
          )
        )
    } yield tracerProvider

  override def otel4zTracing(
      instrumentationScopeName: String
  ): ZLayer[io.opentelemetry.api.OpenTelemetry & ContextStorage, Nothing, Tracing] = OpenTelemetry.tracing(
    instrumentationScopeName = instrumentationScopeName
  )
}
