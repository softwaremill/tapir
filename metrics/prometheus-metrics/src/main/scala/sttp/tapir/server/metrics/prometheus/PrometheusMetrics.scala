package sttp.tapir.server.metrics.prometheus

import io.prometheus.metrics.core.metrics.{Counter, Gauge, Histogram}
import io.prometheus.metrics.expositionformats.ExpositionFormats
import io.prometheus.metrics.model.registry.PrometheusRegistry
import sttp.monad.MonadError
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric, MetricLabels}

import java.io.ByteArrayOutputStream
import java.time.{Clock, Duration}

case class PrometheusMetrics[F[_]](
    namespace: String = "tapir",
    registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
    metrics: List[Metric[F, _]] = List.empty[Metric[F, _]],
    endpointPrefix: EndpointInput[Unit] = "metrics"
) {
  import PrometheusMetrics._

  /** An endpoint exposing the current metric values. */
  lazy val metricsEndpoint: ServerEndpoint[Any, F] = ServerEndpoint.public(
    endpoint.get.in(endpointPrefix).out(plainBody[PrometheusRegistry]),
    (monad: MonadError[F]) => (_: Unit) => monad.eval(Right(registry): Either[Unit, PrometheusRegistry])
  )

  /** Registers a `$namespace_request_active{path, method}` gauge (assuming default labels). */
  def addRequestsActive(labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestActive(registry, namespace, labels))

  /** Registers a `$namespace_request_total{path, method, status}` counter (assuming default labels). */
  def addRequestsTotal(labels: MetricLabels = MetricLabels.Default): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestTotal(registry, namespace, labels))

  /** Registers a `$namespace_request_duration_seconds{path, method, status, phase}` histogram (assuming default labels). */
  def addRequestsDuration(labels: MetricLabels = MetricLabels.Default, clock: Clock = Clock.systemUTC()): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestDuration(registry, namespace, labels, clock))

  /** Registers a custom metric. */
  def addCustom(m: Metric[F, _]): PrometheusMetrics[F] = copy(metrics = metrics :+ m)

  /** The interceptor which can be added to a server's options, to enable metrics collection. */
  def metricsInterceptor(ignoreEndpoints: Seq[AnyEndpoint] = Seq.empty): MetricsRequestInterceptor[F] =
    new MetricsRequestInterceptor[F](metrics, ignoreEndpoints :+ metricsEndpoint.endpoint)
}

object PrometheusMetrics {

  implicit val schemaForPrometheusRegistry: Schema[PrometheusRegistry] = Schema.string[PrometheusRegistry]

  private val prometheusExpositionFormat = ExpositionFormats.init()

  implicit val prometheusRegistryCodec: Codec[String, PrometheusRegistry, CodecFormat.TextPlain] =
    Codec.anyString(TextPlain())(_ => DecodeResult.Value(new PrometheusRegistry()))(r => {
      val output = new ByteArrayOutputStream()
      prometheusExpositionFormat.getPrometheusTextFormatWriter.write(output, r.scrape())
      output.close()
      output.toString
    })

  private def metricNameWithNamespace(namespace: String, metricName: String) = s"${namespace}_${metricName}"

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
      registry: PrometheusRegistry = PrometheusRegistry.defaultRegistry,
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

  def requestActive[F[_]](registry: PrometheusRegistry, namespace: String, labels: MetricLabels): Metric[F, Gauge] =
    Metric[F, Gauge](
      Gauge
        .builder()
        .name(metricNameWithNamespace(namespace, "request_active"))
        .help("Active HTTP requests")
        .labelNames(labels.namesForRequest: _*)
        .register(registry),
      onRequest = { (req, gauge, m) =>
        m.unit {
          EndpointMetric()
            .onEndpointRequest { ep => m.eval(gauge.labelValues(labels.valuesForRequest(ep, req): _*).inc()) }
            .onResponseBody { (ep, _) => m.eval(gauge.labelValues(labels.valuesForRequest(ep, req): _*).dec()) }
            .onException { (ep, _) => m.eval(gauge.labelValues(labels.valuesForRequest(ep, req): _*).dec()) }
        }
      }
    )

  def requestTotal[F[_]](registry: PrometheusRegistry, namespace: String, labels: MetricLabels): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .builder()
        .name(metricNameWithNamespace(namespace, "request_total"))
        .help("Total HTTP requests")
        .labelNames(labels.namesForRequest ++ labels.namesForResponse: _*)
        .register(registry),
      onRequest = { (req, counter, m) =>
        m.unit {
          EndpointMetric()
            .onResponseBody { (ep, res) =>
              m.eval(counter.labelValues(labels.valuesForRequest(ep, req) ++ labels.valuesForResponse(res): _*).inc())
            }
            .onException { (ep, ex) =>
              m.eval(counter.labelValues(labels.valuesForRequest(ep, req) ++ labels.valuesForResponse(ex): _*).inc())
            }
        }
      }
    )

  def requestDuration[F[_]](
      registry: PrometheusRegistry,
      namespace: String,
      labels: MetricLabels,
      clock: Clock = Clock.systemUTC()
  ): Metric[F, Histogram] =
    Metric[F, Histogram](
      Histogram
        .builder()
        .name(metricNameWithNamespace(namespace, "request_duration_seconds"))
        .help("Duration of HTTP requests")
        .labelNames(labels.namesForRequest ++ labels.namesForResponse ++ List(labels.forResponsePhase.name): _*)
        .register(registry),
      onRequest = { (req, histogram, m) =>
        m.eval {
          val requestStart = clock.instant()
          def duration = Duration.between(requestStart, clock.instant()).toMillis.toDouble / 1000.0
          EndpointMetric()
            .onResponseHeaders { (ep, res) =>
              m.eval(
                histogram
                  .labelValues(
                    labels.valuesForRequest(ep, req) ++ labels.valuesForResponse(res) ++ List(labels.forResponsePhase.headersValue): _*
                  )
                  .observe(duration)
              )
            }
            .onResponseBody { (ep, res) =>
              m.eval(
                histogram
                  .labelValues(
                    labels.valuesForRequest(ep, req) ++ labels.valuesForResponse(res) ++ List(labels.forResponsePhase.bodyValue): _*
                  )
                  .observe(duration)
              )
            }
            .onException { (ep, ex) =>
              m.eval(
                histogram
                  .labelValues(
                    labels.valuesForRequest(ep, req) ++ labels.valuesForResponse(ex) ++ List(labels.forResponsePhase.bodyValue): _*
                  )
                  .observe(duration)
              )
            }
        }
      }
    )
}
