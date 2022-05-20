# Observability

Metrics collection is possible by creating `Metric` instances and adding them to server options via `MetricsInterceptor`. 
Certain endpoints can be ignored by adding their definitions to `ignoreEndpoints` list.

`Metric` wraps an aggregation object (like a counter or gauge), and needs to implement the `onRequest` function, which
returns an `EndpointMetric` instance.

`Metric.onRequest` is used to create the proper metric description. Any additional data might be gathered there, like
getting current timestamp and passing it down to `EndpointMetric` callbacks which are then executed in certain points of
request processing.

There are three callbacks in `EndpointMetric`:

1. `onEndpointRequest` - called after a request matches an endpoint (inputs are successfully decoded), or when decoding
   inputs fails, but a downstream interceptor provides a response.
2. `onResponse` - called after response is assembled. Note that the response body might be lazily produced. To properly
   observe end-of-body, use the provided `BodyListener`.
3. `onException` - called after exception is thrown (in underlying streamed body, and/or on any other exception when
   there's no default response)

### Labels

By default, request metrics are labeled by path and method. Response labels are additionally labelled by status code
group. For example GET endpoint like `http://h:p/api/persons?name=Mike` returning 200 response will be labeled
as `path="api/persons", method="GET", status="2xx"`. Query params are omitted by default, but it's possible to include
them as shown in example below.

If the path contains captures, the label will include the path capture name instead of the actual value, e.g.
`api/persons/{name}`.

Labels for default metrics can be customized, any attribute from `Endpoint`, `ServerRequest` and `ServerResponse`
could be used, for example:

```scala
import sttp.tapir.server.metrics.MetricLabels

val labels = MetricLabels(
  forRequest = List(
    "path" -> { case (ep, _) => ep.showPathTemplate() },
    "protocol" -> { case (_, req) => req.protocol }
  ),
  forResponse = Nil
)
```

## Prometheus metrics

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % "1.0.0-RC1"
```

`PrometheusMetrics` encapsulates `CollectorReqistry` and `Metric` instances. It provides several ready to use metrics as
well as an endpoint definition to read the metrics & expose them to the Prometheus server.

For example, using `AkkaServerInterpeter`:

```scala
import akka.http.scaladsl.server.Route
import io.prometheus.client.CollectorRegistry
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.akkahttp.{AkkaHttpServerInterpreter, AkkaHttpServerOptions}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// an instance with default metrics; use PrometheusMetrics[Future]() for an empty one
val prometheusMetrics = PrometheusMetrics.default[Future]()

// enable metrics collection
val serverOptions: AkkaHttpServerOptions = AkkaHttpServerOptions
  .customiseInterceptors
  .metricsInterceptor(prometheusMetrics.metricsInterceptor())
  .options

// route which exposes the current metrics values
val routes: Route = AkkaHttpServerInterpreter(serverOptions).toRoute(prometheusMetrics.metricsEndpoint)
```

By default, the following metrics are exposed:

* `tapir_request_active{path, method}` (gauge)
* `tapir_request_total{path, method, status}` (counter)
* `tapir_request_duration_seconds{path, method, status, phase}` (histogram)

The namespace and label names/values can be customised when creating the `PrometheusMetrics` instance.

### Custom metrics

To create and add custom metrics:

```scala
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import io.prometheus.client.{CollectorRegistry, Counter}
import scala.concurrent.Future

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
        .onResponseBody { (ep, res) =>
          Future.successful {
            val path = ep.showPathTemplate()
            val method = req.method.method
            val status = res.code.toString()
            counter.labels(path, method, status).inc()
          }
        }
    )
  }
)

val prometheusMetrics = PrometheusMetrics[Future]("tapir", CollectorRegistry.defaultRegistry)
  .addCustom(responsesTotal)
```

## OpenTelemetry metrics

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-metrics" % "1.0.0-RC1"
```

OpenTelemetry metrics are vendor-agnostic and can be exported using one
of [exporters](https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters) from SDK.

`OpenTelemetryMetrics` encapsulates metric instances and needs a `Meter` from OpenTelemetry API to create
default metrics, simply:

```scala
import sttp.tapir.server.metrics.opentelemetry.OpenTelemetryMetrics
import io.opentelemetry.api.metrics.{Meter, MeterProvider}
import scala.concurrent.Future

val provider: MeterProvider = ???
val meter: Meter = provider.get("instrumentation-name")

val metrics = OpenTelemetryMetrics.default[Future](meter)

val metricsInterceptor = metrics.metricsInterceptor() // add to your server options
```