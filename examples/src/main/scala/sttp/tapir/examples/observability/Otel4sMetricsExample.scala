// {cat=Observability; effects=cats-effect; server=Netty; json=circe}: Otel4s collecting metrics

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.45
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server-cats:1.11.45
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.45
//> using dep com.softwaremill.sttp.tapir::tapir-otel4s-metrics:1.11.45
//> using dep "org.typelevel::otel4s-oteljava:0.13.2"
//> using deps io.opentelemetry:opentelemetry-exporter-otlp:1.54.0
//> using dep "io.opentelemetry:opentelemetry-sdk-extension-autoconfigure:1.54.0"
//> using dep ch.qos.logback:logback-classic:1.5.18

package sttp.tapir.examples.observability

import cats.effect
import cats.effect.std.Dispatcher
import cats.effect.{IO, IOApp}
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.netty.cats.{NettyCatsServer, NettyCatsServerOptions}
import sttp.tapir.server.metrics.otel4s.Otel4sMetrics
import org.slf4j.{Logger, LoggerFactory}
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.oteljava.OtelJava

import scala.concurrent.duration.*
import sttp.tapir.server.ServerEndpoint

import scala.io.StdIn

/** This example application demonstrates how to implement metrics in a Scala application using the <a
  * href="https://github.com/typelevel/otel4s"> otel4s library </a>. More info about oltel4s you may find <a
  * href="https://typelevel.org/otel4s/">here</a>
  *
  * In order to run otel collector and collect metrics in Prometheus format, you can run it using Docker with the following command:
  * {{{
  *   cat > otel-config.yaml << 'EOF'
  *   receivers:
  *     otlp:
  *       protocols:
  *         grpc:
  *           endpoint: 0.0.0.0:4317
  *   exporters:
  *     prometheus:
  *       endpoint: 0.0.0.0:8889
  *   service:
  *     pipelines:
  *       metrics:
  *         receivers: [otlp]
  *         exporters: [prometheus]
  *   EOF
  *
  *   docker run -p 4317:4317 -p 8889:8889 -v $(pwd)/otel-config.yaml:/etc/otelcol-contrib/config.yaml otel/opentelemetry-collector-contrib:latest
  * }}}
  * You can find the collected metrics in prometheus format at http://localhost:8889/metrics
  */
object Otel4sMetricsExample extends IOApp.Simple:
  private val logger: Logger = LoggerFactory.getLogger(this.getClass.getName)

  override def run: IO[Unit] = otel
    .use { otel4s =>
      otel4s.meterProvider
        .get("sttp.tapir.examples.observability")
        .flatMap { case given Meter[IO] => server() }
    }

  private def server()(using meter: Meter[IO]): IO[Unit] =
    Dispatcher
      .parallel[IO]
      .map(dispatcher =>
        NettyCatsServer(
          NettyCatsServerOptions
            .customiseInterceptors(dispatcher)
            .metricsInterceptor(Otel4sMetrics.default(meter).metricsInterceptor())
            .options
        )
      )
      .use { server =>
        for {
          bind <- server
            .port(8080)
            .host("localhost")
            .addEndpoint(personEndpoint)
            .start()
          _ <- IO
            .blocking {
              logger.info(s"""Server started. Try it with: curl -X POST ${bind.hostName}:${bind.port}/person -d '{"name": "Jacob"}'""")
              logger.info("Press ENTER key to exit.")
              StdIn.readLine()

            }
            .guarantee(bind.stop())
        } yield ()
      }

  private def otel: effect.Resource[IO, OtelJava[IO]] = {
    // Please notice in your, production cases could be better to use env variable instead.
    // Under following link you may find more details about configuration https://typelevel.org/otel4s/sdk/configuration.html
    System.setProperty("otel.java.global-autoconfigure.enabled", "true")
    System.setProperty("otel.service.name", "Otel4s-Metrics-Example")
    OtelJava.autoConfigured[IO]()
  }

  case class Person(name: String)

  // Simple endpoint returning 200 or 400 response with string body
  val personEndpoint: ServerEndpoint[Any, IO] =
    endpoint.post
      .in("person")
      .in(jsonBody[Person])
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic { p =>
        IO.sleep(3.seconds) *>
          IO.pure(Either.cond(p.name == "Jacob", "Welcome", "Unauthorized"))
      }
