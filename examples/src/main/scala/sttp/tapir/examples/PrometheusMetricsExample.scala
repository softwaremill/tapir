package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object PrometheusMetricsExample extends App with StrictLogging {

  case class Person(name: String)

  // Simple endpoint returning 200 or 400 response with string body
  val personEndpoint: ServerEndpoint[Any, Future] =
    endpoint.post
      .in("person")
      .in(jsonBody[Person])
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic { p => Future.successful { if (p.name == "Jacob") Right("Welcome") else Left("Unauthorized") } }

  val prometheusMetrics = PrometheusMetrics.default[Future]()

  val serverOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customiseInterceptors
      // Adds an interceptor which collects metrics by executing callbacks
      .metricsInterceptor(prometheusMetrics.metricsInterceptor())
      .options

  val routes: Route =
    AkkaHttpServerInterpreter(serverOptions).toRoute(
      List(
        personEndpoint,
        // Exposes GET endpoint under `metrics` path for prometheus and serializes metrics from `CollectorRegistry` to plain text response
        prometheusMetrics.metricsEndpoint
      )
    )

  implicit val actorSystem: ActorSystem = ActorSystem()

  Await.result(Http().newServerAt("localhost", 8080).bindFlow(routes), 1.minute)

  logger.info(
    "Server started. POST persons under http://localhost:8080/person and then GET metrics from http://localhost:8080/metrics"
  )
}
