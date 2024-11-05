// {cat=Observability; effects=Future; server=Netty; json=circe}: Reporting DataDog metrics

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-datadog-metrics:1.11.8
//> using dep org.slf4j:slf4j-api:2.0.13

package sttp.tapir.examples.observability

import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.datadog.DatadogMetrics
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.io.StdIn

import org.slf4j.{Logger, LoggerFactory}

@main def datadogMetricsExample(): Unit =
  val logger: Logger = LoggerFactory.getLogger(this.getClass.getName)

  case class Person(name: String)

  // Simple endpoint returning 200 or 400 response with string body
  val personEndpoint: ServerEndpoint[Any, Future] =
    endpoint.post
      .in("person")
      .in(jsonBody[Person])
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic { p =>
        Thread.sleep(3000)
        Future.successful { if (p.name == "Jacob") Right("Welcome") else Left("Unauthorized") }
      }

  // Datadog Agent hostname and port
  val hostname = "localhost"
  val port = 8125

  val client =
    new NonBlockingStatsDClientBuilder()
      .hostname(hostname)
      .port(port)
      .build()

  val datadogMetrics = DatadogMetrics.default[Future](client)

  val serverOptions: NettyFutureServerOptions =
    NettyFutureServerOptions.customiseInterceptors
      // Adds an interceptor which collects metrics by executing callbacks
      .metricsInterceptor(datadogMetrics.metricsInterceptor())
      .options

  val program = for {
    binding <- NettyFutureServer().port(8080).addEndpoint(personEndpoint, serverOptions).start()
    _ <- Future {
      logger.info(s"""Server started. Try it with: curl -X POST localhost:8080/person -d '{"name": "Jacob"}'""")
      logger.info(s"The metrics are sent to udp://$hostname:$port")
      logger.info("Press ENTER key to exit.")
      StdIn.readLine()
    }
    stop <- binding.stop()
  } yield stop

  Await.result(program, Duration.Inf)
