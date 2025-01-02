package sttp.tapir.examples.observability

import cats.effect.std.{Console, Random}
import cats.effect.{Async, IO, IOApp}
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.netty.cats.NettyCatsServer
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
  *
  * The example code requires the following dependencies:
  * {{{
  *   val openTelemetryVersion = <OpenTelemetry version>
  *   val otel4sVersion = <Otel4s version>
  *
  *   libraryDependencies ++= Seq(
  *     "org.typelevel" %% "otel4s-oteljava" % otel4sVersion,
  *      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % openTelemetryVersion,
  *     "io.opentelemetry" % "opentelemetry-exporter-otlp" % openTelemetryVersion
  *    )
  * }}}
  */

class Service[F[_]: Async: Tracer: Console]:
  def doWork(steps: Int): F[Unit] = doWorkInternal(steps)

  private def doWorkInternal(steps: Int): F[Unit] = {
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

    if (steps > 0) step *> doWorkInternal(steps - 1) else step
  }

class HttpApi[F[_]: Async: Tracer](service: Service[F]):
  import HttpApi.*
  private val doWork: PublicEndpoint[Work, Unit, Unit, Any] = endpoint.post.in("work").in(jsonBody[Work])

  private val doWorkServerEndpoint: ServerEndpoint[Any, F] = doWork.serverLogicSuccess(work => {
    Tracer[F].span("DoWorkRequest").use { span =>
      span.addEvent("Do the work request received") *>
        service.doWork(work.steps) *>
        span.addEvent("Finished working.")
    }
  })

  val all: List[ServerEndpoint[Any, F]] = List(doWorkServerEndpoint)

  object HttpApi:
    case class Work(steps: Int)

object Otel4sTracingExample extends IOApp.Simple:
  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getName)
  private val port = 9090
  private val host = "localhost"

  override def run: IO[Unit] = otel
    .use { otel4s =>
      otel4s.tracerProvider
        .get("sttp.tapir.examples.observability")
        .flatMap(implicit tracer => server(HttpApi[IO](Service[IO])))
    }

  private def server(httpApi: HttpApi[IO]): IO[Unit] =
    NettyCatsServer
      .io()
      .use { server =>
        for {
          bind <- server
            .port(port)
            .host(host)
            .addEndpoints(httpApi.all)
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

  private def otel = {
    // We implemented customization here to set service name.
    // Please notice in your, production cases could be better to use env variable instead.
    // Under following link you may find more details about configuration https://typelevel.org/otel4s/sdk/configuration.html
    def customize(a: AutoConfigOtelSdkBuilder): AutoConfigOtelSdkBuilder = {
      val customResource = Resource.getDefault
        .merge(
          Resource.create(
            Attributes.of(io.opentelemetry.api.common.AttributeKey.stringKey("service.name"), "Otel4sTracingExample")
          )
        )
      a.addResourceCustomizer((resource, config) => customResource)
      a
    }

    OtelJava
      .autoConfigured[IO](customize)
  }
