# Observability

Metrics collection is possible by extending `Metric` and adding `MetricsInterceptor` to server options.
`Metric` provides three callback methods which are called in certain points of request processing:

1. `onRequest` - called after successful request decoding
2. `onResponse` - called after response is assembled
3. `onDecodeFailure` - called after decoding fails

## Prometheus metrics

`PrometheusMetrics` encapsulates `CollectorReqistry` and `Metric` instances.
It provides several ready to use metrics as well as endpoint definition and codec for exposing them to Prometheus.

For example, using `AkkaServerInterpeter`:
```scala mdoc:compile-only
  import akka.http.scaladsl.server.Route
  import io.prometheus.client.CollectorRegistry
  import sttp.monad.FutureMonad
  import sttp.tapir.metrics.prometheus.PrometheusMetrics
  import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val monad: FutureMonad = new FutureMonad()

  val prometheusMetrics = PrometheusMetrics(CollectorRegistry.defaultRegistry)
    .withRequestsTotal
    .withRequestsFailure
    .withResponsesTotal
    .withResponsesDuration

  implicit val serverOptions: AkkaHttpServerOptions =
    AkkaHttpServerOptions.customInterceptors(additionalInterceptors = List(prometheusMetrics.metricsInterceptor()))

  val routes: Route = AkkaHttpServerInterpreter.toRoute(prometheusMetrics.metricsServerEndpoint)
```