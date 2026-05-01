package sttp.tapir.server.o11y.otel4z

import io.opentelemetry.api
import zio._
import zio.logging.backend.SLF4J
import zio.telemetry.opentelemetry.context.ContextStorage

import zio.telemetry.opentelemetry.OpenTelemetry

import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.logs.SdkLoggerProvider


/** ZIOTelBase is a trait that provides a ZIO layer for OpenTelemetry as bootstrap.
  *
  * By default, it uses the OTEL_EXPORTER_OTLP_ENDPOINT environment variable to configure the OpenTelemetry exporter.
  *
  *   - Uses SLF4J for logging to stdout.
  *   - Logs, Metrics, Traces are sent to the OpenTelemetry collector through gRPC.
  */
protected trait ZIOtelBase {
  this: ZIOApp =>

  /** The name of the resource, advertised to the OpenTelemetry collector. */
  def resourceName: String

  def withZIOMetrics: Boolean = true

 
  /** The environment for the ZIOpenTelemetry trait.
    *
    * This is the environment that will be used to run the ZIO application, hence provided by bootstrap.
    * 
    * It includes:
    *  - the OpenTelemetry instance.
    *  - the ContextStorage instance.
    */
  override type Environment = api.OpenTelemetry with ContextStorage

  /** The tag for the ZIOpenTelemetry trait. */
  def environmentTag: Tag[Environment] =
    Tag[Environment]


  /**
    * The console log layer for the ZIOpenTelemetry trait.
    *
    * Default implementation uses SLF4J for logging to stdout.
    */
  def consoleLogLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  /**
    * The OpenTelemetry providers for the ZIOpenTelemetry trait.
    *
    * @return
    */
  def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = ZIO.none

  def meterProvider: URIO[Scope, Option[SdkMeterProvider]] =  ZIO.none

  def tracerProvider: URIO[Scope, Option[SdkTracerProvider]]  = ZIO.none


  final def otelProviders: URIO[Scope, OtelProviders] =  for {
        logger <- logProvider
        meter <- meterProvider
        tracer <- tracerProvider
      } yield OtelProviders(tracer, meter, logger)
    
  /** The bootstrap layer for the ZIOpenTelemetry trait.
    *
    * This is the layer that will be used to bootstrap the ZIO application. It includes the OpenTelemetry layer, the Tracing layer, and the
    * Meter layer.
    */
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Environment] =
    consoleLogLayer >>> 
      OpenTelemetry.contextZIO >+> (ZLayer.scoped(otelProviders) >>>
       ZIOtelLayer
          .live(withZIOMetrics))

  
  
}
