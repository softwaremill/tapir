package sttp.tapir.metrics.prometheus

import io.prometheus.client.CollectorRegistry.defaultRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}
import sttp.monad.MonadError
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir._
import sttp.tapir.metrics.{EndpointMetric, Metric}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

import java.io.StringWriter
import java.time.{Duration, Instant}

case class PrometheusMetrics[F[_]](
    namespace: String,
    registry: CollectorRegistry,
    metrics: List[Metric[F, _]] = List.empty[Metric[F, _]]
) {
  import PrometheusMetrics._

  lazy val metricsEndpoint: ServerEndpoint[Unit, Unit, CollectorRegistry, Any, F] = ServerEndpoint(
    endpoint.get.in("metrics").out(plainBody[CollectorRegistry]),
    (monad: MonadError[F]) => _ => monad.eval(Right(registry).withLeft[Unit])
  )

  def withRequestsTotal(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestsTotal(registry, namespace, labels))
  def withRequestsActive(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics :+ requestsActive(registry, namespace, labels))
  def withResponsesTotal(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics :+ responsesTotal(registry, namespace, labels))
  def withResponsesDuration(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics :+ responsesDuration(registry, namespace, labels))
  def withCustom(m: Metric[F, _]): PrometheusMetrics[F] = copy(metrics = metrics :+ m)

  def metricsInterceptor[B](ignoreEndpoints: Seq[Endpoint[_, _, _, _]] = Seq.empty): MetricsRequestInterceptor[F, B] =
    new MetricsRequestInterceptor[F, B](metrics, ignoreEndpoints :+ metricsEndpoint.endpoint)
}

object PrometheusMetrics {

  implicit val schemaForCollectorRegistry: Schema[CollectorRegistry] = Schema.string[CollectorRegistry]

  implicit val collectorRegistryCodec: Codec[String, CollectorRegistry, CodecFormat.TextPlain] =
    Codec.anyStringCodec(TextPlain())(_ => DecodeResult.Value(new CollectorRegistry()))(r => {
      val output = new StringWriter()
      TextFormat.write004(output, r.metricFamilySamples)
      output.close()
      output.toString
    })

  def withDefaultMetrics[F[_]](namespace: String = "tapir", labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
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

  def requestsTotal[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels): Metric[F, Counter] =
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

  def requestsActive[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels): Metric[F, Gauge] =
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

  def responsesTotal[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace(namespace)
        .name("responses_total")
        .help("HTTP responses")
        .labelNames(labels.forRequestNames ++ labels.forResponseNames: _*)
        .register(registry),
      onRequest = { (req, counter, m) =>
        m.unit {
          EndpointMetric()
            .onResponse { (ep, res) => m.eval(counter.labels(labels.forRequest(ep, req) ++ labels.forResponse(res): _*).inc()) }
            .onException { (ep, _) => m.eval(counter.labels(labels.forRequest(ep, req) :+ "5xx": _*).inc()) }
        }
      }
    )

  def responsesDuration[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels): Metric[F, Histogram] =
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
          val requestStart = Instant.now()
          EndpointMetric()
            .onResponse { (ep, res) =>
              m.eval(
                histogram
                  .labels(labels.forRequest(ep, req) ++ labels.forResponse(res): _*)
                  .observe(Duration.between(requestStart, Instant.now()).toMillis.toDouble / 1000.0)
              )
            }
        }
      }
    )

  case class PrometheusLabels(
      forRequest: Seq[(String, (Endpoint[_, _, _, _], ServerRequest) => String)],
      forResponse: Seq[(String, ServerResponse[_] => String)]
  ) {
    def forRequestNames: Seq[String] = forRequest.map { case (name, _) => name }
    def forResponseNames: Seq[String] = forResponse.map { case (name, _) => name }
    def forRequest(ep: Endpoint[_, _, _, _], req: ServerRequest): Seq[String] = forRequest.map { case (_, f) => f(ep, req) }
    def forResponse(res: ServerResponse[_]): Seq[String] = forResponse.map { case (_, f) => f(res) }
  }

  /** Labels request by path and method, response by status code
    */
  lazy val DefaultLabels: PrometheusLabels = PrometheusLabels(
    forRequest = Seq(
      "path" -> { case (ep, _) => ep.renderPathTemplate(renderQueryParam = None) },
      "method" -> { case (_, req) => req.method.method }
    ),
    forResponse = Seq(
      "status" -> { res =>
        res.code match {
          case c if c.isInformational => "1xx"
          case c if c.isSuccess       => "2xx"
          case c if c.isRedirect      => "3xx"
          case c if c.isClientError   => "4xx"
          case c if c.isServerError   => "5xx"
          case _                      => ""
        }
      }
    )
  )
}
