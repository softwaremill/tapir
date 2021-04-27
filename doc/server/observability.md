# Observability

Metrics collection is possible by creating `Metric` instances and adding them to server options via `MetricsInterceptor`
. Certain endpoints can be ignored by adding their definitions to `ignoreEndpoints` list.

`Metric` wraps an aggregation object (like counter or gauge), and requires a `onRequest` function
returning `EndpointMetric` with metric collection logic.

`Metric.onRequest` is used to produce proper metric description and any additional operation might be started there,
like getting current timestamp and passing it down to `EndpointMetric` callbacks which are then executed in certain
points of request processing.

There are three callbacks in `EndpointMetric`:

1. `onRequest` - called after successful request decoding
2. `onResponse` - called after response is assembled
3. `onException` - called after exception is thrown (in underlying streamed body, and/or on any other exception when
   there's no default response)

## Prometheus metrics

`PrometheusMetrics` encapsulates `CollectorReqistry` and `Metric` instances. It provides several ready to use metrics as
well as endpoint definition and codec for exposing them to Prometheus.

For example, using `AkkaServerInterpeter`:

```scala mdoc:compile-only
  import akka.http.scaladsl.server.Route
  import io.prometheus.client.CollectorRegistry
  import sttp.monad.FutureMonad
  import sttp.tapir.metrics.prometheus.PrometheusMetrics
  import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.Future

  implicit val monad: FutureMonad = new FutureMonad()

  val prometheusMetrics = PrometheusMetrics[Future]("tapir", CollectorRegistry.defaultRegistry)
    .withRequestsTotal()
    .withResponsesTotal()
    .withResponsesDuration()

  implicit val serverOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customInterceptors(metricsInterceptor = Some(prometheusMetrics.metricsInterceptor()))

  val routes: Route = AkkaHttpServerInterpreter.toRoute(prometheusMetrics.metricsEndpoint.serverLogic { _ =>
    Future.successful(Right(prometheusMetrics.registry).withLeft[Unit])
  })
```

By default, request metrics are labeled by path and method. Response labels are additionally labelled by status code
group. For example GET endpoint like `http://h:p/api/persons?name=Mike` returning 200 response will be labeled
as `{path="api/persons", method="GET", status="2xx"}`. Query params are omitted by default, but it's possible to include
them as shown in example below.

Labels for default metrics can be customized, any attribute from `Endpoint`, `ServerRequest` and `ServerResponse`
could be used, for example:

```scala mdoc:invisible
  import io.prometheus.client.CollectorRegistry
  import sttp.tapir.metrics.prometheus.PrometheusMetrics
  import scala.concurrent.Future
```

```scala mdoc:compile-only
  import sttp.tapir.metrics.prometheus.PrometheusMetrics.PrometheusLabels

  val labels = PrometheusLabels(
    forRequest = Seq(
      "path" -> { case (ep, _) => ep.renderPathTemplate() },
      "protocol" -> { case (_, req) => req.protocol }
    ),
    forResponse = Seq()
  )

  val prometheusMetrics = PrometheusMetrics[Future]("tapir", CollectorRegistry.defaultRegistry).withRequestsTotal(labels)
```

Also, custom metric creation is possible and attaching it to `PrometheusMetrics`, for example:

```scala mdoc:invisible
  import io.prometheus.client.{CollectorRegistry, Counter}
  import sttp.tapir.metrics.prometheus.PrometheusMetrics
  import sttp.tapir.metrics.{EndpointMetric, Metric}
  import scala.concurrent.Future
```

```scala mdoc:compile-only
  // Metric for counting responses labeled by path, method and status code
  val responsesTotal = Metric[Future, Counter](
    Counter
      .build()
      .namespace("tapir")
      .name("responses_total")
      .help("HTTP responses")
      .labelNames("path", "method", "status")
      .register(CollectorRegistry.defaultRegistry),
    onRequest = { (req, counter, _) =>
      Future.successful(
        EndpointMetric()
          .onResponse { (ep, res) =>
            Future.successful {
              val path = ep.renderPathTemplate()
              val method = req.method.method
              val status = res.code.toString()
              counter.labels(path, method, status).inc()
            }
          }
      )
    }
  )
  
  val prometheusMetrics = PrometheusMetrics[Future]("tapir", CollectorRegistry.defaultRegistry).withCustom(responsesTotal)
```
