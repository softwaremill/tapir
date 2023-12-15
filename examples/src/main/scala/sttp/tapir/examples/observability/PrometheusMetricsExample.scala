package sttp.tapir.examples.observability

import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.io.StdIn

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

  val serverOptions: NettyFutureServerOptions =
    NettyFutureServerOptions.customiseInterceptors
      // Adds an interceptor which collects metrics by executing callbacks
      .metricsInterceptor(prometheusMetrics.metricsInterceptor())
      .options

  val endpoints =
    List(
      personEndpoint,
      // Exposes GET endpoint under `metrics` path for prometheus and serializes metrics from `PrometheusRegistry` to plain text response
      prometheusMetrics.metricsEndpoint
    )

  val program = for {
    binding <- NettyFutureServer().port(8080).addEndpoints(endpoints, serverOptions).start()
    _ <- Future {
      logger.info(s"""Server started. Try it with: curl -X POST localhost:8080/person -d '{"name": "Jacob"}'""")
      logger.info("The metrics are available at http://localhost:8080/metrics")
      logger.info("Press ENTER key to exit.")
      StdIn.readLine()
    }
    stop <- binding.stop()
  } yield stop

  Await.result(program, Duration.Inf)
}
