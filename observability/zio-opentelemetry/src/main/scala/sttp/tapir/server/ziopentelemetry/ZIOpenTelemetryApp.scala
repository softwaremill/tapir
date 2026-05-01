package sttp.tapir.server.ziopentelemetry

import zio._
import io.opentelemetry.api
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import zio.telemetry.opentelemetry.context.ContextStorage
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.ServiceAttributes
import io.opentelemetry.semconv.DeploymentAttributes
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.OpenTelemetrySdk
import zio.telemetry.opentelemetry.metrics.Meter
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder

/** OtelProviders is a case class that holds the OpenTelemetry providers for tracing, metrics and logging.
  *
  * It is used to build the OpenTelemetry
  *
  * @param tracerProvider
  * @param meterProvider
  * @param loggerProvider
  */
case class OtelProviders(
    tracerProvider: Option[SdkTracerProvider],
    meterProvider: Option[SdkMeterProvider],
    loggerProvider: Option[SdkLoggerProvider]
) {

  def build(customize: OpenTelemetrySdkBuilder => Unit): OpenTelemetrySdk = {
    val builder = OpenTelemetrySdk
      .builder()
    tracerProvider.foreach(builder.setTracerProvider)
    meterProvider.foreach(builder.setMeterProvider)
    loggerProvider.foreach(builder.setLoggerProvider)
    customize(builder)
    builder.buildAndRegisterGlobal()
  }

}

trait ZIOpenTelemetryApp extends ZIOApp {

  type Environment <: api.OpenTelemetry with ContextStorage

  def resourceName: String

  def version: Option[String]

  def environment: Option[String]

  /** The OpenTelemetry logging layer for the ZIOpenTelemetry trait.
    *
    * By default, no logging layer is provided. When mixing in the [[Logging]] trait, this layer is provided, and will be used by bootstrap.
    */
  protected def otel4zLogging: URLayer[api.OpenTelemetry with ContextStorage, Unit] = ZLayer.unit

  protected def zioMetrics: URLayer[api.OpenTelemetry with ContextStorage, Unit] = ZLayer.unit

  /** The OpenTelemetry [[SdkLoggerProvider]] for the ZIOpenTelemetry trait.
    *
    * By default, no logger provider is provided. You can override this to provide a logger provider, e.g. to export logs in OTLP gRPC
    * format to collector.
    *
    * Or mixing in the [[Logging]] trait, which provides a logger provider that exports logs in OTLP gRPC format to collector.
    */
  protected def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = ZIO.none

  protected def meterProvider: URIO[Scope, Option[SdkMeterProvider]] = ZIO.none

  protected def tracerProvider: URIO[Scope, Option[SdkTracerProvider]] = ZIO.none

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

  /** The console log layer, provide a console logger to the ZIO application.
    *
    * Default implementation does nothing, hence uses the default ZIO console logger, which logs to stdout.
    *
    * You should override this to use a different logger:
    *   - No logs at all:
    *
    * {{{
    *   override def consoleLogLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers
    * }}}
    *
    *   - SLF4J, Logback, etc. To use SLF4J, you can use the following layer:
    *     {{{
    *   override def consoleLogLayer: ZLayer[Any, Nothing, Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j
    *     }}}
    */
  def consoleLogLayer: ZLayer[Any, Nothing, Unit] = ZLayer.unit

  /** The OpenTelemetry metrics layer for the ZIOpenTelemetry trait.
    *
    * This is a no-op implementation, which does not export metrics, when mixin the [[Metrics]] trait, it will export metrics in OTLP gRPC
    */
  def otel4zMetrics(
      instrumentationScopeName: String
  ): URLayer[io.opentelemetry.api.OpenTelemetry & ContextStorage, Meter] = ZLayer.succeed(noop.NoopMeter(instrumentationScopeName))

  /** The OpenTelemetry tracing layer for the ZIOpenTelemetry trait.
    *
    * This is a no-op implementation, which does not export traces, when mixin the [[Tracing]] trait, it will export traces in OTLP gRPC.
    */
  def otel4zTracing(
      instrumentationScopeName: String
  ): ZLayer[io.opentelemetry.api.OpenTelemetry & ContextStorage, Nothing, Tracing] =
    ZLayer.succeed(noop.NoopTracing(instrumentationScopeName))

  /** Customize the OpenTelemetry builder.
    *
    * This method is called by the [[openTelemetryLive]] layer to customize the OpenTelemetry builder.
    *
    * You can override this method to customize the OpenTelemetry builder, e.g. to add a custom resource, or to add a custom span processor.
    */
  def customizeOpenTelemetry(builder: OpenTelemetrySdkBuilder): Unit = {}

  /** The OpenTelemetry layer for the ZIOpenTelemetry trait.
    *
    * This is the layer that will be used to provide the OpenTelemetry to the ZIO application.
    */
  def openTelemetryLive: ZLayer[OtelProviders, Nothing, OpenTelemetrySdk] = ZLayer.scoped[OtelProviders](
    for {
      otelProviders <- ZIO.service[OtelProviders]
      openTelemetry <- ZIO.fromAutoCloseable(
        ZIO.succeed(otelProviders.build(customizeOpenTelemetry))
      )

    } yield openTelemetry
  )

  def observabilityLayer: ULayer[ContextStorage & OpenTelemetrySdk & Meter & Tracing] =
    consoleLogLayer >>>
      OpenTelemetry.contextZIO >+> (otelProviders >>>
        openTelemetryLive) >+> (otel4zLogging ++ zioMetrics ++ otel4zMetrics(resourceName) ++ otel4zTracing(resourceName))
}
