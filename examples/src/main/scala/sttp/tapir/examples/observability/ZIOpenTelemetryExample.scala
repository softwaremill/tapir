// {cat=Observability; effects=ZIO; server=ZIO HTTP}: Tracing requests with ZIO OpenTelemetry (customised config)

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.13.26
//> using dep com.softwaremill.sttp.tapir::tapir-zio:1.13.26
//> using dep com.softwaremill.sttp.tapir::tapir-zio-http-server:1.13.26
//> using dep com.softwaremill.sttp.tapir::tapir-zio-opentelemetry:1.13.26
//> using dep dev.zio::zio-opentelemetry:3.1.17
//> using dep io.opentelemetry:opentelemetry-sdk:1.64.0
//> using dep io.opentelemetry:opentelemetry-exporter-otlp:1.64.0
//> using dep io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.64.0

package sttp.tapir.examples.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import sttp.tapir.server.ziopentelemetry.{ZIOpenTelemetryTracing, ZIOpenTelemetryTracingConfig}
import sttp.tapir.ztapir.*

import zio.*
import zio.http.*
import zio.telemetry.opentelemetry.OpenTelemetry as ZioOpenTelemetry
import zio.telemetry.opentelemetry.tracing.Tracing

/** Traces each request handled by a tapir endpoint, using the `ZIOpenTelemetryTracing` interceptor with a customised
  * [[ZIOpenTelemetryTracingConfig]] - here, adding an extra attribute to every request span on top of the defaults.
  *
  * The OpenTelemetry SDK is autoconfigured from the standard `OTEL_*` environment variables; set e.g. `OTEL_EXPORTER_OTLP_ENDPOINT` and
  * `OTEL_SERVICE_NAME` to export traces to a collector.
  */
object ZIOpenTelemetryExample extends ZIOAppDefault:

  /** The OpenTelemetry SDK, autoconfigured from `OTEL_*` env variables, closed when the app shuts down. */
  private val openTelemetryLayer: ZLayer[Any, Throwable, OpenTelemetry] =
    ZLayer.scoped(
      ZIO
        .fromAutoCloseable(ZIO.attempt(AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk))
        .map(sdk => sdk: OpenTelemetry)
    )

  /** A zio-telemetry `Tracing` service, built from the OpenTelemetry SDK. */
  private val tracingLayer: ZLayer[Any, Throwable, Tracing] =
    (openTelemetryLayer ++ ZioOpenTelemetry.contextZIO) >>> ZioOpenTelemetry.tracing(instrumentationScopeName = "zio-observability")

  /** Customising the configuration is optional: `ZIOpenTelemetryTracing(tracing)` uses a sensible default config (following the
    * OpenTelemetry semantic conventions). Here we override it to reuse the default attributes and add a custom one to every request span.
    */
  private val tracingConfig: ZIOpenTelemetryTracingConfig = ZIOpenTelemetryTracingConfig(
    requestAttributes = request =>
      Attributes
        .builder()
        .putAll(ZIOpenTelemetryTracingConfig.Defaults.requestAttributes(request))
        .put(AttributeKey.stringKey("tapir.example"), "custom")
        .build()
  )

  private def serverOptions(tracing: Tracing): ZioHttpServerOptions[Any] =
    ZioHttpServerOptions.customiseInterceptors
      // the config is optional - `ZIOpenTelemetryTracing(tracing)` uses the default configuration
      .prependInterceptor(ZIOpenTelemetryTracing(tracing, tracingConfig))
      .options

  private def endpoints(using tracing: Tracing): List[ServerEndpoint[Any, Task]] =
    import tracing.aspects.*
    val hello: ServerEndpoint[Any, Task] = sttp.tapir.endpoint.get
      .in("hello")
      .out(stringBody)
      // the user logic runs inside a child span, which correlates with the request span created by the interceptor
      .zServerLogic(_ => ZIO.logInfo("Handling /hello request").as("Hello, World!") @@ span("hello-logic"))
    List(hello)

  override def run =
    (for
      tracing <- ZIO.service[Tracing]
      _ <- Console.printLine("Starting server on http://localhost:8080")
      httpApp = ZioHttpInterpreter(serverOptions(tracing)).toHttp(endpoints(using tracing))
      _ <- Server.serve(httpApp).provide(Server.default)
    yield ()).provide(tracingLayer)
