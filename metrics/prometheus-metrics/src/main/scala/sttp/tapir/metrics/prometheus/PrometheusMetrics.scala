package sttp.tapir.metrics.prometheus

import io.prometheus.client.CollectorRegistry.defaultRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}
import sttp.monad.MonadError
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metric.{EndpointMetric, Metric, MetricLabels}

import java.io.StringWriter
import java.time.{Clock, Duration}

case class PrometheusMetrics[F[_]](
    namespace: String,
    registry: CollectorRegistry,
    metrics: List[Metric[F, _]] = List.empty[Metric[F, _]]
) {
  import PrometheusMetrics._

  lazy val metricsEndpoint: ServerEndpoint[Any, F] = ServerEndpoint.public(
    endpoint.get.in("metrics").out(plainBody[CollectorRegistry]),
    (monad: MonadError[F]) => (_: Unit) => monad.eval(Right(registry): Either[Unit, CollectorRegistry])
  )

  def withRequestsTotal(labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestsTotal(registry, namespace, labels))
  def withRequestsActive(labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestsActive(registry, namespace, labels))
  def withResponsesTotal(labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    copy(metrics = metrics :+ responsesTotal(registry, namespace, labels))
  def withResponsesDuration(labels: MetricLabels = MetricLabels.Default, clock: Clock = Clock.systemUTC()): PrometheusMetrics[F] =
    copy(metrics = metrics :+ responsesDuration(registry, namespace, labels, clock))
  def withCustom(m: Metric[F, _]): PrometheusMetrics[F] = copy(metrics = metrics :+ m)

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

  def withDefaultMetrics[F[_]](namespace: String = "tapir", labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    PrometheusMetrics(
      namespace,
      defaultRegistry,
      List(
        requestsTotal(defaultRegistry, namespace, labels),
        requestsActive(defaultRegistry, namespace, labels),
        responsesTotal(defaultRegistry, namespace, labels),
        responsesDuration(defaultRegistry, namespace, labels)
      )
    )

  def requestsTotal[F[_]](registry: CollectorRegistry, namespace: String, labels: MetricLabels): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace(namespace)
        .name("requests_total")
        .help("Total HTTP requests")
        .labelNames(labels.forRequestNames: _*)
        .create()
        .register(registry),
      onRequest = { (req, counter, m) =>
        m.unit {
          EndpointMetric().onEndpointRequest { ep => m.eval(counter.labels(labels.forRequest(ep, req): _*).inc()) }
        }
      }
    )

  def requestsActive[F[_]](registry: CollectorRegistry, namespace: String, labels: MetricLabels): Metric[F, Gauge] =
    Metric[F, Gauge](
      Gauge
        .build()
        .namespace(namespace)
        .name("requests_active")
        .help("Active HTTP requests")
        .labelNames(labels.forRequestNames: _*)
        .create()
        .register(registry),
      onRequest = { (req, gauge, m) =>
        m.unit {
          EndpointMetric()
            .onEndpointRequest { ep => m.eval(gauge.labels(labels.forRequest(ep, req): _*).inc()) }
            .onResponse { (ep, _) => m.eval(gauge.labels(labels.forRequest(ep, req): _*).dec()) }
            .onException { (ep, _) => m.eval(gauge.labels(labels.forRequest(ep, req): _*).dec()) }
        }
      }
    )

  def responsesTotal[F[_]](registry: CollectorRegistry, namespace: String, labels: MetricLabels): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace(namespace)
        .name("responses_total")
        .help("Total HTTP responses")
        .labelNames(labels.forRequestNames ++ labels.forResponseNames: _*)
        .register(registry),
      onRequest = { (req, counter, m) =>
        m.unit {
          EndpointMetric()
            .onResponse { (ep, res) => m.eval(counter.labels(labels.forRequest(ep, req) ++ labels.forResponse(res): _*).inc()) }
            .onException { (ep, ex) => m.eval(counter.labels(labels.forRequest(ep, req) ++ labels.forResponse(ex): _*).inc()) }
        }
      }
    )

  def responsesDuration[F[_]](
      registry: CollectorRegistry,
      namespace: String,
      labels: MetricLabels,
      clock: Clock = Clock.systemUTC()
  ): Metric[F, Histogram] =
    Metric[F, Histogram](
      Histogram
        .build()
        .namespace(namespace)
        .name("responses_duration")
        .help("HTTP responses duration")
        .labelNames(labels.forRequestNames ++ labels.forResponseNames: _*)
        .register(registry),
      onRequest = { (req, histogram, m) =>
        m.unit {
          val requestStart = clock.instant()
          EndpointMetric()
            .onResponse { (ep, res) =>
              m.eval(
                histogram
                  .labels(labels.forRequest(ep, req) ++ labels.forResponse(res): _*)
                  .observe(Duration.between(requestStart, clock.instant()).toMillis.toDouble / 1000.0)
              )
            }
            .onException { (ep, ex) =>
              m.eval(
                histogram
                  .labels(labels.forRequest(ep, req) ++ labels.forResponse(ex): _*)
                  .observe(Duration.between(requestStart, clock.instant()).toMillis.toDouble / 1000.0)
              )
            }
        }
      }
    )
}
