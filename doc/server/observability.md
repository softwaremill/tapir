# Observability

Metrics collection is possible by creating `Metric` instances and adding them to server options via `MetricsInterceptor`. 
Certain endpoints can be ignored by adding their definitions to `ignoreEndpoints` list.
`Metric` wraps an aggregation object (like counter or gauge), and requires a function
returning `EndpointMetric` with metric collection logic. There are three callbacks in `EndpointMetric`:

1. `onRequest` - called after successful request decoding
2. `onResponse` - called after response is assembled
3. `onException` - called after exception is thrown (this callback will be ignored if exception handling is done
   with `ExceptionInterceptor` returning some default response, `onResponse` will be invoked then)

## Prometheus metrics

`PrometheusMetrics` encapsulates `CollectorReqistry` and `Metric` instances. It provides several ready to use metrics as
well as endpoint definition and codec for exposing them to Prometheus. Request metrics are labeled by path and method.
Response labels are additionally labelled by status code group.

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

Labels for default metrics can be customized, any attribute from `Endpoint`, `ServerRequest` and `ServerResponse` could
be used, for example:

```scala mdoc:invisible
  import io.prometheus.client.CollectorRegistry
  import sttp.tapir.metrics.prometheus.PrometheusMetrics
  import scala.concurrent.Future
```

```scala mdoc:compile-only
  import sttp.tapir.metrics.prometheus.PrometheusMetrics.PrometheusLabels

  val labels = PrometheusLabels(
    forRequest = Seq("protocol" -> { case (_, req) => req.protocol }),
    forResponse = Seq()
  )

  val prometheusMetrics = PrometheusMetrics[Future]("tapir", CollectorRegistry.defaultRegistry).withRequestsTotal(labels)
```
