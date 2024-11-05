// {cat=Observability; effects=Future; server=Netty; json=circe}: Reporting Prometheus metrics

//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-netty-server:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.8
//> using dep com.softwaremill.sttp.tapir::tapir-prometheus-metrics:1.11.8
//> using dep org.slf4j:slf4j-api:2.0.13

package sttp.tapir.examples.observability

import io.circe.generic.auto.*
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.netty.{NettyFutureServer, NettyFutureServerOptions}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.io.StdIn

@main def prometheusMetricsExample(): Unit =
  val logger: Logger = LoggerFactory.getLogger(this.getClass().getName)

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
