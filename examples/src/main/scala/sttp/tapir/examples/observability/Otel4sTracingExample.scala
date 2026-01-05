// {cat=Observability; effects=cats-effect; server=Netty; json=circe}: Otel4s collecting traces

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.20
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-cats:1.11.20
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.20
//> using dep com.softwaremill.sttp.tapir::tapir-opentelemetry-metrics:1.13.2
//> using dep com.softwaremill.sttp.tapir::tapir-otel4s-tracing:1.13.2
//> using dep "org.typelevel::otel4s-oteljava:0.12.0-RC3"
//> using dep "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.47.0"
//> using dep ch.qos.logback:logback-classic:1.5.17

package sttp.tapir.examples.observability

import cats.effect
import cats.effect.std.{Console, Dispatcher, Random}
import cats.effect.{Async, IO, IOApp}
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.netty.cats.{NettyCatsServer, NettyCatsServerOptions}
import sttp.tapir.server.tracing.otel4s.Otel4sTracing
import org.slf4j.{Logger, LoggerFactory}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.trace.Tracer
import cats.syntax.all.*
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder as AutoConfigOtelSdkBuilder
import org.typelevel.otel4s.oteljava.OtelJava
import io.opentelemetry.sdk.resources.Resource

import scala.concurrent.duration.*
import sttp.tapir.server.ServerEndpoint

import scala.io.StdIn

/** This example application demonstrates how to implement distributed tracing in a Scala application using the <a
  * href="https://github.com/typelevel/otel4s"> otel4s library </a>. More info about oltel4s you may find <a
  * href="https://typelevel.org/otel4s/">here</a>
  *
  * In order to collect and visualize traces, you can run Jaeger using Docker with the following command:
  * {{{
  *   docker run --name jaeger -e COLLECTOR_OTLP_ENABLED=true -p 16686:16686 -p 4317:4317 -p 4318:4318 jaegertracing/all-in-one:1.35
  * }}}
  * Jaeger UI is available at http://localhost:16686. You can find the collected traces there.
  */
object Otel4sTracingExample extends IOApp.Simple:
  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override def run: IO[Unit] = otel
    .use { otel4s =>
      otel4s.tracerProvider
        .get("sttp.tapir.examples.observability")
        .flatMap { case given Tracer[IO] => server(HttpApi[IO](Service[IO])) }
    }

  private def server(httpApi: HttpApi[IO])(using tracer: Tracer[IO]): IO[Unit] =
    Dispatcher
      .parallel[IO]
      .map(dispatcher =>
        NettyCatsServer(
          NettyCatsServerOptions
            .default[IO](dispatcher)
            .prependInterceptor(Otel4sTracing(tracer))
        )
      )
      .use { server =>
        for {
          bind <- server
            .port(9090)
            .host("localhost")
            .addEndpoint(httpApi.doWorkServerEndpoint)
            .start()
          _ <- IO
            .blocking {
              logger.info(s"""Server started. Try it with: curl -X POST ${bind.hostName}:${bind.port}/work -d '{"steps": 10}'""")
              logger.info("Press ENTER key to exit.")
              StdIn.readLine()

            }
            .guarantee(bind.stop())
        } yield ()
      }

  private def otel: effect.Resource[IO, OtelJava[IO]] = {
    // We implemented customization here to set service name.
    // Please notice in your, production cases could be better to use env variable instead.
    // Under following link you may find more details about configuration https://typelevel.org/otel4s/sdk/configuration.html
    def customize(a: AutoConfigOtelSdkBuilder): AutoConfigOtelSdkBuilder = {
      val customResource = Resource.getDefault.merge(
        Resource.create(Attributes.of(io.opentelemetry.api.common.AttributeKey.stringKey("service.name"), "Otel4s-Tracing-Example"))
      )
      a.addResourceCustomizer((resource, config) => customResource)
      a
    }
    OtelJava.autoConfigured[IO](customize)
  }

class Service[F[_]: Async: Tracer: Console]:
  def doWork(steps: Int): F[Unit] = {
    val step = Tracer[F]
      .span("internal-work", Attribute("step", steps.toLong))
      .surround {
        for {
          random <- Random.scalaUtilRandom
          delay <- random.nextIntBounded(1000)
          _ <- Async[F].sleep(delay.millis)
          _ <- Console[F].println("Do work ...")
        } yield ()
      }

    if (steps > 0) step *> doWork(steps - 1) else step
  }

class HttpApi[F[_]: Async: Tracer](service: Service[F]):
  private case class Work(steps: Int)

  val doWorkServerEndpoint: ServerEndpoint[Any, F] = endpoint.post
    .in("work")
    .in(jsonBody[Work])
    .serverLogicSuccess(work => {
      Tracer[F].span("DoWorkRequest").use { span =>
        span.addEvent("Do the work request received") *>
          service.doWork(work.steps) *>
          span.addEvent("Finished working.")
      }
    })
