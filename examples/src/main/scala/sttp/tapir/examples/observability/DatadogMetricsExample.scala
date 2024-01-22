package sttp.tapir.examples.observability

import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import sttp.tapir.examples.logging.Logging
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

object DatadogMetricsExample extends App with Logging {

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
}
