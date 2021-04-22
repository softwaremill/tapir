package sttp.tapir.examples

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.concat
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.prometheus.client.{CollectorRegistry, Counter}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.metrics.Metric
import sttp.tapir.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object PrometheusMetricsExample extends App with StrictLogging {

  case class Person(name: String)

  // Simple endpoint returning 200 or 400 response with string body
  val personEndpoint: ServerEndpoint[Person, String, String, Any, Future] =
    endpoint.post
      .in("person")
      .in(jsonBody[Person])
      .out(stringBody)
      .errorOut(stringBody)
      .serverLogic { p => Future.successful { if (p.name == "Jacob") Right("Welcome") else Left("Unauthorized") } }

  val collectorRegistry = CollectorRegistry.defaultRegistry

  // Metric for counting responses labeled by path, method and status code
  val responsesTotal = Metric[Counter](
    Counter
      .build()
      .namespace("tapir")
      .name("responses_total")
      .help("HTTP responses")
      .labelNames("path", "method", "status")
      .register(collectorRegistry)
  ).onResponse { (_, req, res, _, counter) => // this callback will be executed after request processing
    val path = req.uri.pathSegments.toString
    val method = req.method.method
    val status = res.code.toString()
    counter.labels(path, method, status).inc()

  }

  val prometheusMetrics = PrometheusMetrics("tapir", collectorRegistry)
    // default metric collecting all requests and custom one
    .withRequestsTotal()
    .withCustom(responsesTotal)

  val metricsEndpoint: ServerEndpoint[Unit, Unit, CollectorRegistry, Any, Future] =
    prometheusMetrics.metricsEndpoint.serverLogic { _ =>
      Future.successful(Right(prometheusMetrics.registry).withLeft[Unit])
    }

  implicit val serverOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customInterceptors(
      // Adds an interceptor which collects metrics by executing callbacks
      metricsInterceptor = Some(prometheusMetrics.metricsInterceptor())
    )

  val routes: Route = concat(
    AkkaHttpServerInterpreter.toRoute(personEndpoint),
    // Exposes GET endpoint under `metrics` path for prometheus and serializes metrics from `CollectorRegistry` to plain text response
    AkkaHttpServerInterpreter.toRoute(metricsEndpoint)
  )

  implicit val actorSystem: ActorSystem = ActorSystem()

  Await.result(Http().newServerAt("localhost", 8080).bindFlow(routes), 1.minute)

  logger.info(
    "Server started. POST persons under http://localhost:8080/person and then GET metrics from http://localhost:8080/metrics"
  )
}
