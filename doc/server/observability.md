# Observability

Metrics collection is possible by extending `Metric` and adding `MetricsInterceptor` to server options.
`Metric` provides three callback methods which are called in certain points of request processing:

1. `onRequest` - after successful request decoding
2. `onResponse` - after response is assembled

## Prometheus metrics

`PrometheusMetrics` encapsulates `CollectorReqistry` and `Metric` instances.
It provides several ready to use metrics as well as endpoint definition and codec for exposing them to Prometheus.
Request metrics are labeled by path and method. Response labels are additionally labelled by status code group.

For example, using `AkkaServerInterpeter`:
```scala mdoc:compile-only
import akka.http.scaladsl.server.Route
  import io.prometheus.client.CollectorRegistry
  import sttp.monad.FutureMonad
  import sttp.tapir.metrics.prometheus.PrometheusMetrics
  import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions, _}

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val monad: FutureMonad = new FutureMonad()

  val prometheusMetrics = PrometheusMetrics("tapir", CollectorRegistry.defaultRegistry)
    .withRequestsTotal()
    .withResponsesTotal()
    .withResponsesDuration()

  implicit val serverOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customInterceptors(additionalInterceptors = List(prometheusMetrics.metricsInterceptor()))

  val routes: Route = AkkaHttpServerInterpreter.toRoute(prometheusMetrics.metricsServerEndpoint)
```

Labels for default metrics can be customized, any attribute from `Endpoint`, `ServerRequest` and `ServerResponse` could be used, for example:
```scala mdoc:compile-only
val labels = PrometheusLabels(
    forRequest = Seq("protocol" -> { case (_, req) => req.protocol }),
    forResponse = Seq()
  )

  val prometheusMetrics = PrometheusMetrics("tapir", collectorRegistry).withRequestsTotal(labels)
```
