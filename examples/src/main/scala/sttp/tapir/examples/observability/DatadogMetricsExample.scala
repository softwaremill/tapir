package sttp.tapir.examples.observability

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.timgroup.statsd.NonBlockingStatsDClientBuilder
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.metrics.datadog.DatadogMetrics

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object DatadogMetricsExample extends App with StrictLogging {
  implicit val actorSystem: ActorSystem = ActorSystem()
  import actorSystem.dispatcher

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

  val serverOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customiseInterceptors
      // Adds an interceptor which collects metrics by executing callbacks
      .metricsInterceptor(datadogMetrics.metricsInterceptor())
      .options

  val routes: Route = AkkaHttpServerInterpreter(serverOptions).toRoute(personEndpoint)

  Await.result(Http().newServerAt("localhost", 8080).bindFlow(routes), 1.minute)

  logger.info(
    s"Server started. POST persons under http://localhost:8080/person. The metrics sends to udp://$hostname:$port"
  )
}
