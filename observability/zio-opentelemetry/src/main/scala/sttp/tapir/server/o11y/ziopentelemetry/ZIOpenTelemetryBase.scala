package sttp.tapir.server.o11y.ziopentelemetry

import io.opentelemetry.api
import zio._
import zio.telemetry.opentelemetry.context.ContextStorage

import zio.telemetry.opentelemetry.OpenTelemetry

import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.DeploymentAttributes

/** ZIOTelBase is a trait that provides a ZIO layer for OpenTelemetry as bootstrap.
  *
  * By default, it uses the OTEL_EXPORTER_OTLP_ENDPOINT environment variable to configure the OpenTelemetry exporter.
  *
  *   - Logs, Metrics, Traces are sent to the OpenTelemetry collector through gRPC.
  */
protected trait ZIOpenTelemetryBase {
  this: ZIOApp =>

  /** The name of the resource, advertised to the OpenTelemetry collector. */
  def resourceName: String

  /** The version of the resource, advertised to the OpenTelemetry collector. */
  def version: Option[String]

  /** The environment of the resource, advertised to the OpenTelemetry collector. */
  def environment: Option[String]

  /** Extra attributes to be added to the resource. */
  def extraAttributes: Attributes = Attributes.empty

  /** The attributes of the resource, advertised to the OpenTelemetry collector. */
  def attributes: Attributes = {
    val builder = Attributes
      .builder()
      .put(ServiceAttributes.SERVICE_NAME, resourceName)
    version.foreach(v => builder.put(ServiceAttributes.SERVICE_VERSION, v))
    environment.foreach(e => builder.put(DeploymentAttributes.DEPLOYMENT_ENVIRONMENT_NAME, e))
    builder.putAll(extraAttributes).build()
  }

  /** Whether to enable ZIO internal metrics.
    *
    * This relies on [[ZioMetrics]] which must be provided **early** by the bootstrap layer.
    *
    * Default is true.
    */
  def withZIOMetrics: Boolean = true

  /** The environment for the ZIOpenTelemetry trait.
    *
    * This is the environment that will be used to run the ZIO application, hence provided by bootstrap.
    *
    * It includes:
    *   - the OpenTelemetry instance.
    *   - the ContextStorage instance.
    */
  override type Environment = api.OpenTelemetry with ContextStorage

  /** The tag for the ZIOpenTelemetry trait. */
  def environmentTag: Tag[Environment] =
    Tag[Environment]

  /** The console log layer for the ZIOpenTelemetry trait.
    *
    * Default implementation uses the default ZIO console logger, which logs to stdout. You can override this to use a different logger,
    * e.g. SLF4J, Logback, etc. To use SLF4J, you can use the following layer:
    * {{{
    *  def consoleLogLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
    * }}}
    */
  def consoleLogLayer: ZLayer[Any, Nothing, Unit] = ZLayer.unit

  /** The OpenTelemetry [[SdkLoggerProvider]] for the ZIOpenTelemetry trait.
    *
    * By default, no logger provider is provided. You can override this to provide a logger provider, e.g. to export logs in OTLP gRPC
    * format to collector.
    *
    * Or mixing in the [[Logging]] trait, which provides a logger provider that exports logs in OTLP gRPC format to collector.
    */
  def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = ZIO.none

  /** The OpenTelemetry [[SdkMeterProvider]] for the ZIOpenTelemetry trait. By default, no meter provider is provided. You can override this
    * to provide a meter provider, e.g. to export metrics in OTLP gRPC format to collector.
    *
    * Or mixing in the [[Metrics]] trait, which provides a meter provider that exports metrics in OTLP gRPC format to collector.
    */
  def meterProvider: URIO[Scope, Option[SdkMeterProvider]] = ZIO.none

  /** The OpenTelemetry [[SdkTracerProvider]] for the ZIOpenTelemetry trait. By default, no tracer provider is provided. You can override
    * this to provide a tracer provider, e.g. to export traces in OTLP gRPC format to collector.
    *
    * Or mixing in the [[Traces]] trait, which provides a tracer provider that exports traces in OTLP gRPC format to collector.
    */
  def tracerProvider: URIO[Scope, Option[SdkTracerProvider]] = ZIO.none

  /** The OpenTelemetry providers for the ZIOpenTelemetry trait.
    *
    * This is the layer that will be used to provide the OpenTelemetry providers to the ZIO application. It includes the OpenTelemetry
    * logger provider, the OpenTelemetry meter provider, and the OpenTelemetry tracer provider.
    */
  final def otelProviders: ULayer[OtelProviders] = ZLayer.scoped[Any](for {
    logger <- logProvider
    meter <- meterProvider
    tracer <- tracerProvider
  } yield OtelProviders(tracer, meter, logger))

  /** The bootstrap layer for the ZIOpenTelemetry trait.
    *
    * This is the layer that will be used to bootstrap the ZIO application. It includes the OpenTelemetry layer, the Tracing layer, and the
    * Meter layer.
    */
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Environment] =
    consoleLogLayer >>>
      OpenTelemetry.contextZIO >+> (otelProviders >>>
        ZIOpenTelemetryLayer
          .live(withZIOMetrics))

}
