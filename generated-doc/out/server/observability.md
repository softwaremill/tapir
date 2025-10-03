# Observability

Observability includes metrics & tracing integrations.

## Metrics

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

## Metric labels

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
"com.softwaremill.sttp.tapir" %% "tapir-prometheus-metrics" % "1.11.47"
```

`PrometheusMetrics` encapsulates `PrometheusReqistry` and `Metric` instances. It provides several ready to use metrics as
well as an endpoint definition to read the metrics & expose them to the Prometheus server.

For example, using `NettyFutureServerInterpreter`:

```scala
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.netty.{NettyFutureServerInterpreter, NettyFutureServerOptions, FutureRoute}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

// an instance with default metrics; use PrometheusMetrics[Future]() for an empty one
val prometheusMetrics = PrometheusMetrics.default[Future]()

// enable metrics collection
val serverOptions: NettyFutureServerOptions = NettyFutureServerOptions
  .customiseInterceptors
  .metricsInterceptor(prometheusMetrics.metricsInterceptor())
  .options

// route which exposes the current metrics values
val routes: FutureRoute = NettyFutureServerInterpreter(serverOptions).toRoute(prometheusMetrics.metricsEndpoint)
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
import io.prometheus.metrics.core.metrics.{Counter, Gauge, Histogram}
import io.prometheus.metrics.model.registry.PrometheusRegistry
import scala.concurrent.Future

// Metric for counting responses labeled by path, method and status code
val responsesTotal = Metric[Future, Counter](
  Counter
    .builder()
    .name("tapir_responses_total")
    .help("HTTP responses")
    .labelNames("path", "method", "status")
    .register(PrometheusRegistry.defaultRegistry),
  onRequest = { (req, counter, _) =>
    Future.successful(
      EndpointMetric()
        .onResponseBody { (ep, res) =>
          Future.successful {
            val path = ep.showPathTemplate()
            val method = req.method.method
            val status = res.code.toString()
            counter.labelValues(path, method, status).inc()
          }
        }
    )
  }
)

val prometheusMetrics = PrometheusMetrics[Future]("tapir", PrometheusRegistry.defaultRegistry)
  .addCustom(responsesTotal)
```

## OpenTelemetry metrics

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-metrics" % "1.11.47"
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

## otel4s OpenTelemetry metrics

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-otel4s-metrics" % "1.11.47"
```

The `Otel4sMetrics` provides integration with the [otel4s](https://typelevel.org/otel4s/) library for OpenTelemetry metrics.
This allows you to create metrics for your tapir endpoints using a purely functional API.

`Otel4sMetrics` encapsulates metric instances and needs a `Meter[F]` from `otel4s` to create default metrics.

It should be set as `metricsInterceptor` of your ServerOptions: 

Example using Http4s:
```scala
import cats.effect.IO
import org.typelevel.otel4s.oteljava.OtelJava
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.otel4s.Otel4sMetrics

OtelJava
  .autoConfigured[IO]()
  .use { otel4s =>
    otel4s.meterProvider.get("meter-name").flatMap { meter =>
      val endpoints: List[ServerEndpoint[Any, IO]] = ???
      val routes =
        Http4sServerInterpreter[IO](
          Http4sServerOptions
            .customiseInterceptors[IO]
            .metricsInterceptor(Otel4sMetrics.default(meter).metricsInterceptor())
            .options
        ).toRoutes(endpoints)
      // start your server
      ???
    }
  }
```

By default, the following metrics are exposed:

* `http.server.active_requests` (up-down-counter)
* `http.server.requests.total` (counter)
* `http.server.request.duration` (histogram)



## Datadog Metrics

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-datadog-metrics" % "1.11.47"
```

Datadog metrics are sent as Datadog custom metrics through
[DogStatsD](https://docs.datadoghq.com/developers/dogstatsd/) protocol.

`DatadogMetrics` uses `StatsDClient` to send the metrics, and its settings such as host, port, etc. depend on it.
For example:

```scala
import com.timgroup.statsd.{NonBlockingStatsDClientBuilder, StatsDClient}
import sttp.tapir.server.metrics.datadog.DatadogMetrics
import scala.concurrent.Future

val statsdClient: StatsDClient = new NonBlockingStatsDClientBuilder()
  .hostname("localhost")   // Datadog Agent's hostname
  .port(8125)              // Datadog Agent's port (UDP)
  .build()

val metrics = DatadogMetrics.default[Future](statsdClient)
```

### Custom Metrics

To create and add custom metrics:

```scala
import com.timgroup.statsd.{NonBlockingStatsDClientBuilder, StatsDClient}
import sttp.tapir.server.metrics.datadog.DatadogMetrics
import sttp.tapir.server.metrics.datadog.DatadogMetrics.Counter
import sttp.tapir.server.metrics.{EndpointMetric, Metric}
import scala.concurrent.Future

val statsdClient: StatsDClient = new NonBlockingStatsDClientBuilder()
  .hostname("localhost")
  .port(8125)
  .build()

// Metric for counting responses labeled by path, method and status code
val responsesTotal = Metric[Future, Counter](
  Counter("tapir.responses_total.count")(statsdClient),
  onRequest = (req, counter, _) =>
    Future.successful(
      EndpointMetric()
        .onResponseBody { (ep, res) =>
          Future.successful {
            val labels = List(
              s"path:${ep.showPathTemplate()}", 
              s"method:${req.method.method}",
              s"status:${res.code.toString()}"
            )
            counter.increment(labels)
          }
        }
    )
)

val datadogMetrics = DatadogMetrics.default[Future](statsdClient)
  .addCustom(responsesTotal)
```

## Zio Metrics

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-zio-metrics" % "1.11.47"
```

Metrics have been integrated into ZIO core in ZIO2.

[Monitoring a ZIO Application Using ZIO's Built-in Metric System](https://zio.dev/guides/tutorials/monitor-a-zio-application-using-zio-built-in-metric-system/).

### Collecting Metrics
```scala
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import zio.{Task, ZIO}

val metrics: ZioMetrics[Task] = ZioMetrics.default[Task]()
val metricsInterceptor: MetricsRequestInterceptor[Task] = metrics.metricsInterceptor()
```

### Example Publishing Metrics Endpoint

Zio metrics publishing functionality is provided by the zio ecosystem library [zio-metrics-connectors](https://github.com/zio/zio-metrics-connectors).  

[Dependencies/Examples](https://zio.dev/guides/tutorials/monitor-a-zio-application-using-zio-built-in-metric-system/#adding-dependencies-to-the-project)
```scala
libraryDependencies += "dev.zio" %% "zio-metrics-connectors" % "2.0.0-RC6"
```

Example zio metrics prometheus publisher style tapir metrics endpoint.
```scala
import sttp.tapir.{endpoint, stringBody}
import zio.*
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus.{PrometheusPublisher, prometheusLayer, publisherLayer}
import zio.metrics.jvm.DefaultJvmMetrics

object ZioEndpoint:
  /** DefaultJvmMetrics.live.orDie >+> is optional if you want JVM metrics */
  private val layer = DefaultJvmMetrics.live.orDie >+> ZLayer.make[PrometheusPublisher](
    ZLayer.succeed(MetricsConfig(1.seconds)),
    prometheusLayer,
    publisherLayer
  )
  
  private val unsafeLayers = Unsafe.unsafe { implicit u =>
    Runtime.unsafe.fromLayer(layer)
  }

  def getMetricsEffect: ZIO[Any, Nothing, String] =
    Unsafe.unsafe { implicit u =>
      unsafeLayers.run(ZIO
        .serviceWithZIO[PrometheusPublisher](_.get)
      )
    }

  val metricsEndpoint =
    endpoint.get.in("metrics").out(stringBody).serverLogicSuccess(_ => getMetricsEffect)
```

## OpenTelemetry tracing

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-tracing" % "1.11.47"
```

OpenTelemetry tracing is vendor-agnostic and can be exported using an exporters, such as Jaeger, Zipkin, DataDog, 
Grafana, etc.

Currently, a `OpenTelemetryTracing` interceptor is available, which creates a span for each request, populating the
context appropriately (with context extracted from the request headers, the request method, path, status code, etc.).
Any spans created as part of the server's logic are then correlated with the request-span, into a single trace.

To propagate the context, the configured OpenTelemetry `ContextStorage` is used, which by default is 
`ThreadLocal`-based, which works with synchronous/direct-style environments, including ones leveraging Ox and virtual
threads. [[Future]]s are supported through instrumentation provided by the 
[OpenTelemetry javaagent](https://opentelemetry.io/docs/zero-code/java/agent/). For functional effect systems, usually
a dedicated integration library is required.

The interceptor should be added before any others, so that it handles the request early. E.g.:

```scala
import io.opentelemetry.api.OpenTelemetry
import sttp.tapir.server.netty.sync.{NettySyncServer, NettySyncServerOptions}
import sttp.tapir.server.tracing.opentelemetry.OpenTelemetryTracing

val otel: OpenTelemetry = ???

val serverOptions: NettySyncServerOptions =
  NettySyncServerOptions.customiseInterceptors
    .prependInterceptor(OpenTelemetryTracing(otel))
    .options

NettySyncServer().options(serverOptions).addEndpoint(???).startAndWait()
```

## otel4s OpenTelemetry tracing

Add the following dependency:

```scala
"com.softwaremill.sttp.tapir" %% "tapir-otel4s-tracing" % "1.11.47"
```

The `Otel4sTracing` interceptor provides integration with the [otel4s](https://typelevel.org/otel4s/) library for OpenTelemetry tracing.
This allows you to create traces for your tapir endpoints using a purely functional API.

`Otel4sTracing` creates a span for each request, extracts context from request headers, and populates spans with relevant metadata (request method, path, status code).
All spans created as part of the request processing will be properly correlated into a single trace.

For details on context propagation with otel4s, see the [official documentation](https://typelevel.org/otel4s/oteljava/tracing-context-propagation.html).

The interceptor should be added before any others to ensure it handles the request early:

Example using Http4s:
```scala
import cats.effect.IO
import org.typelevel.otel4s.oteljava.OtelJava
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.tracing.otel4s.Otel4sTracing

OtelJava
  .autoConfigured[IO]()
  .use { otel4s =>
    otel4s.tracerProvider.get("tracer-name").flatMap { tracer =>
      val endpoints: List[ServerEndpoint[Any, IO]] = ???
      val routes =
        Http4sServerInterpreter[IO](Http4sServerOptions.default[IO].prependInterceptor(Otel4sTracing(tracer)))
          .toRoutes(endpoints)
      // start your server
      ???
    }
  }
```

## Tracing when no endpoints match a request

When no endpoints match a request, the interceptor will still create a span for the request. However, if no response is
returned, no response-related attributes will be added to the span. This is because other routes in the host server
might still serve the request.

If a default response (e.g. a `404 Not Found`) should be produced, this should be enabled using the 
[reject interceptor](errors.md). Such a setup assumes that there are no other routes in the server, after the Tapir
server interpreter is invoked.