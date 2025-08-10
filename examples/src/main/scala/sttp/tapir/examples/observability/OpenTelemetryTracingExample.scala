// {cat=Observability; effects=Direct; server=Netty; json=circe}: OpenTelemetry tracing interceptor

//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.11.18
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.18
//> using dep com.softwaremill.sttp.tapir::tapir-opentelemetry-tracing:1.11.41
//> using dep io.opentelemetry:opentelemetry-exporter-otlp:1.53.0
//> using dep io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.53.0
//> using dep ch.qos.logback:logback-classic:1.5.17

package sttp.tapir.examples.observability

import io.circe.generic.auto.*
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.{Span, Tracer}
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import org.slf4j.{Logger, LoggerFactory}
import ox.*
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions}
import sttp.tapir.server.tracing.opentelemetry.OpenTelemetryTracing

import scala.io.StdIn

/** An example of integrating Tapir with OpenTelemetry tracing. An interceptor creates a span for each request, populating the context
  * appropriately (with the request method, path, status code, etc.). Any spans created as part of the server's logic are then correlated
  * with the request-span, into a single trace.
  *
  * To propagate the context, the default OpenTelemetry `ThreadLocal`-based `ContextStorage` is used. Hence, this approach is only useable
  * with direct-style, "synchronous" servers, including ones leveraging Ox and virtual threads. `Future`- or functional effect-based servers
  * and server logic would not correlate the spans correctly.
  *
  * To collect the traces, you'll need a running OpenTelemetry exporter; additionally, a UI is useful to view the collected data. One option
  * is to use Grafana's LGTM stack, using the following docker-compose file:
  *
  * {{{
  * services:
  *  # OpenTelemetry Collector, Prometheus, Loki, Tempo, Grafana
  *  observability:
  *    image: 'grafana/otel-lgtm'
  *    ports:
  *      - '3000:3000' # Grafana's UI
  *      - '4317:4317' # Exporter
  * }}}
  *
  * The OpenTelemetry instance that is used below is configured using environmental variables; the defaults work fine (as long as an
  * exporter is available at http://localhost:4317), but you might want to customize the service name by setting the `OTEL_SERVICE_NAME=`
  * variable.
  */
object OpenTelemetryTracingExample extends OxApp:
  override def run(args: Vector[String])(using Ox): ExitCode =
    val logger: Logger = LoggerFactory.getLogger(this.getClass().getName)
    val otel: OpenTelemetry = AutoConfiguredOpenTelemetrySdk.initialize().getOpenTelemetrySdk()

    // this Tracer instance is used for reporting spans inside the server logic
    val tracer = otel.getTracer("example")

    case class Person(name: String)

    // Simple endpoint returning 200 or 400 response with string body
    val personEndpoint: ServerEndpoint[Any, Identity] =
      endpoint.post
        .in("person")
        .in(jsonBody[Person])
        .out(stringBody)
        .errorOut(stringBody)
        .handle: p =>
          // Creating some artificial spans to demonstrate how they are correlated into a single trace,
          // together with the span created by the request interceptor
          withSpan(tracer, "handlePersonRequest"): _ =>
            withSpan(tracer, "warmupAuthorization"): _ =>
              Thread.sleep(1000)

            withSpan(tracer, "performAuthorization"): span =>
              Thread.sleep(2000)
              if p.name == "Jane" then
                span.addEvent("authorization successful")
                Right("Welcome")
              else
                span.addEvent("authorization failed")
                Left("Unauthorized")

    val serverOptions: NettySyncServerOptions =
      NettySyncServerOptions.customiseInterceptors
        // The crucial step: adding the OpenTelemetry tracing interceptor, which is called before any other interceptors
        .prependInterceptor(OpenTelemetryTracing(otel))
        .options

    useInScope(NettySyncServer().options(serverOptions).addEndpoint(personEndpoint).start())(_.stop()).discard

    logger.info(s"""Server started. Try it with: curl -X POST http://localhost:8080/person -d '{"name": "Jane"}'""")
    logger.info("Press ENTER key to exit.")
    val _ = StdIn.readLine()

    logger.info("Exiting...")
    ExitCode.Success
  end run

  def withSpan[T](tracer: Tracer, spanName: String)(f: Span => T): T =
    useInterruptible(tracer.spanBuilder(spanName).startSpan(), _.end()): span =>
      useInterruptible(span.makeCurrent(), _.close()): _ =>
        f(span).tapException: e =>
          span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR)
          span.recordException(e).discard

end OpenTelemetryTracingExample
