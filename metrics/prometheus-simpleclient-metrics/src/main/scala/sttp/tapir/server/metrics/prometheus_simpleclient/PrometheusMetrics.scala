package sttp.tapir.server.metrics.prometheus_simpleclient

import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}
import sttp.monad.MonadError
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric, MetricLabels}

import java.io.StringWriter
import java.time.{Clock, Duration}

case class PrometheusMetrics[F[_]](
    namespace: String = "tapir",
    registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
    metrics: List[Metric[F, _]] = List.empty[Metric[F, _]],
    endpointPrefix: EndpointInput[Unit] = "metrics"
) {
  import PrometheusMetrics._

  /** An endpoint exposing the current metric values. */
  lazy val metricsEndpoint: ServerEndpoint[Any, F] = ServerEndpoint.public(
    endpoint.get.in(endpointPrefix).out(plainBody[CollectorRegistry]),
    (monad: MonadError[F]) => (_: Unit) => monad.eval(Right(registry): Either[Unit, CollectorRegistry])
  )

  /** Registers a `$namespace_request_active{path, method}` gauge (assuming default labels). */
  def addRequestsActive(labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestActive(registry, namespace, labels))

  /** Registers a `$namespace_request_total{path, method, status}` counter (assuming default labels). */
  def addRequestsTotal(labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestTotal(registry, namespace, labels))

  /** Registers a `$namespace_request_duration_seconds{path, method, status, phase}` histogram (assuming default labels). */
  def addRequestsDuration(
      labels: MetricLabels = MetricLabels.Default,
      clock: Clock = Clock.systemUTC(),
      bucketsOverride: List[Double] = List.empty
  ): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestDuration(registry, namespace, labels, clock, bucketsOverride))

  /** Registers a custom metric. */
  def addCustom(m: Metric[F, _]): PrometheusMetrics[F] = copy(metrics = metrics :+ m)

  /** The interceptor which can be added to a server's options, to enable metrics collection. */
  def metricsInterceptor(ignoreEndpoints: Seq[AnyEndpoint] = Seq.empty): MetricsRequestInterceptor[F] =
    new MetricsRequestInterceptor[F](metrics, ignoreEndpoints :+ metricsEndpoint.endpoint)
}

object PrometheusMetrics {

  implicit val schemaForCollectorRegistry: Schema[CollectorRegistry] = Schema.string[CollectorRegistry]

  implicit val collectorRegistryCodec: Codec[String, CollectorRegistry, CodecFormat.TextPlain] =
    Codec.anyString(TextPlain())(_ => DecodeResult.Value(new CollectorRegistry()))(r => {
      val output = new StringWriter()
      TextFormat.write004(output, r.metricFamilySamples)
      output.close()
      output.toString
    })

  /** Using the default namespace and labels, registers the following metrics:
    *
    *   - `$namespace_request_active{path, method}` (gauge)
    *   - `$namespace_request_total{path, method, status}` (counter)
    *   - `$namespace_request_duration_seconds{path, method, status, phase}` (histogram)
    *
    * Status is by default the status code class (1xx, 2xx, etc.), and phase can be either `headers` or `body` - request duration is
    * measured separately up to the point where the headers are determined, and then once again when the whole response body is complete.
    */
  def default[F[_]](
      namespace: String = "tapir",
      registry: CollectorRegistry = CollectorRegistry.defaultRegistry,
      labels: MetricLabels = MetricLabels.Default
  ): PrometheusMetrics[F] =
    PrometheusMetrics(
      namespace,
      registry,
      List(
        requestActive(registry, namespace, labels),
        requestTotal(registry, namespace, labels),
        requestDuration(registry, namespace, labels)
      )
    )

  def requestActive[F[_]](registry: CollectorRegistry, namespace: String, labels: MetricLabels): Metric[F, Gauge] =
    Metric[F, Gauge](
      Gauge
        .build()
        .namespace(namespace)
        .name("request_active")
        .help("Active HTTP requests")
        .labelNames(labels.namesForRequest: _*)
        .create()
        .register(registry),
      onRequest = { (req, gauge, m) =>
        val labelValues = labels.valuesForRequest(req)
        m.map(m.eval { gauge.labels(labelValues: _*).inc() }) { _ =>
          EndpointMetric()
            .onResponseBody { (_, _) => m.eval(gauge.labels(labelValues: _*).dec()) }
            .onException { (_, _) => m.eval(gauge.labels(labelValues: _*).dec()) }
            .onInterceptorResponse { _ => m.eval(gauge.labels(labelValues: _*).dec()) }
            .onDecodeFailure { () => m.eval(gauge.labels(labelValues: _*).dec()) }
        }
      }
    )

  /** @param placeholderInterceptorEndpoint
    *   When the response is created by an interceptor (request handler), there's no endpoint with which the metrics might be associated. In
    *   such case, using the placeholder endpoint to generate the labels (all labels must always be generated for all requests).
    */
  def requestTotal[F[_]](
      registry: CollectorRegistry,
      namespace: String,
      labels: MetricLabels,
      placeholderInterceptorEndpoint: AnyEndpoint = endpoint.in("__interceptor__")
  ): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace(namespace)
        .name("request_total")
        .help("Total HTTP requests")
        .labelNames(labels.namesForRequest ++ labels.namesForEndpoint ++ labels.namesForResponse: _*)
        .register(registry),
      onRequest = { (req, counter, m) =>
        m.unit {
          EndpointMetric()
            .onResponseBody { (ep, res) =>
              m.eval(
                counter.labels(labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(res): _*).inc()
              )
            }
            .onException { (ep, ex) =>
              m.eval(counter.labels(labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(ex): _*).inc())
            }
            .onInterceptorResponse { res =>
              m.eval(
                counter
                  .labels(
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(placeholderInterceptorEndpoint) ++ labels
                      .valuesForResponse(res): _*
                  )
                  .inc()
              )
            }
        }
      }
    )

  /** @param placeholderInterceptorEndpoint
    *   When the response is created by an interceptor (request handler), there's no endpoint with which the metrics might be associated. In
    *   such case, using the placeholder endpoint to generate the labels (all labels must always be generated for all requests).
    */
  def requestDuration[F[_]](
      registry: CollectorRegistry,
      namespace: String,
      labels: MetricLabels,
      clock: Clock = Clock.systemUTC(),
      bucketsOverride: List[Double] = List.empty,
      placeholderInterceptorEndpoint: AnyEndpoint = endpoint.in("__interceptor__") // #4966
  ): Metric[F, Histogram] =
    Metric[F, Histogram](
      (if (bucketsOverride.nonEmpty) Histogram.build().buckets(bucketsOverride: _*) else Histogram.build())
        .namespace(namespace)
        .name("request_duration_seconds")
        .help("Duration of HTTP requests")
        .labelNames(labels.namesForRequest ++ labels.namesForEndpoint ++ labels.namesForResponse ++ List(labels.forResponsePhase.name): _*)
        .register(registry),
      onRequest = { (req, histogram, m) =>
        m.eval {
          val requestStart = clock.instant()
          def duration = Duration.between(requestStart, clock.instant()).toMillis.toDouble / 1000.0
          EndpointMetric()
            .onResponseHeaders { (ep, res) =>
              m.eval(
                histogram
                  .labels(
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(res) ++ List(
                      labels.forResponsePhase.headersValue
                    ): _*
                  )
                  .observe(duration)
              )
            }
            .onResponseBody { (ep, res) =>
              m.eval(
                histogram
                  .labels(
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(res) ++ List(
                      labels.forResponsePhase.bodyValue
                    ): _*
                  )
                  .observe(duration)
              )
            }
            .onException { (ep, ex) =>
              m.eval(
                histogram
                  .labels(
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(ex) ++ List(
                      labels.forResponsePhase.bodyValue
                    ): _*
                  )
                  .observe(duration)
              )
            }
            .onInterceptorResponse { res =>
              m.eval(
                histogram
                  .labels(
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(placeholderInterceptorEndpoint) ++ labels
                      .valuesForResponse(res) ++ List(labels.forResponsePhase.bodyValue): _*
                  )
                  .observe(duration)
              )
            }
        }
      }
    )
}
