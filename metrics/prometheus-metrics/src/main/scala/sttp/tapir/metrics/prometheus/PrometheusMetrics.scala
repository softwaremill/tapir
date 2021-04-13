package sttp.tapir.metrics.prometheus

import io.prometheus.client.CollectorRegistry.defaultRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}
import sttp.monad.MonadError
import sttp.tapir.CodecFormat.TextPlain
import sttp.tapir._
import sttp.tapir.metrics.Metric
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.metrics.MetricsInterceptor

import java.io.StringWriter
import scala.concurrent.duration.Deadline

case class PrometheusMetrics[F[_]](
    namespace: String,
    registry: CollectorRegistry,
    metrics: Set[Metric[F, _]] = Set.empty[Metric[F, _]]
)(implicit
    monad: MonadError[F]
) {
  import PrometheusMetrics._

  private lazy val metricsEp = endpoint.get.in("metrics").out(plainBody[CollectorRegistry])

  def withRequestsTotal(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics + requestsTotal(registry, namespace, labels))
  def withRequestsActive(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics + requestsActive(registry, namespace, labels))
  def withResponsesTotal(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics + responsesTotal(registry, namespace, labels))
  def withResponsesDuration(labels: PrometheusLabels = DefaultLabels): PrometheusMetrics[F] =
    copy(metrics = metrics + responsesDuration(registry, namespace, labels))
  def withCustom(m: Metric[F, _]): PrometheusMetrics[F] = copy(metrics = metrics + m)

  def metricsServerEndpoint: ServerEndpoint[Unit, Unit, CollectorRegistry, Any, F] =
    metricsEp.serverLogic { _ => monad.unit(Right(registry)) }

  def metricsInterceptor[B](ignoreEndpoints: Seq[Endpoint[_, _, _, _]] = Seq.empty): Interceptor[F, B] =
    new MetricsInterceptor[F, B](metrics.toList, ignoreEndpoints :+ metricsEp)
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

  def withDefaultMetrics[F[_]](namespace: String = "tapir", labels: PrometheusLabels = DefaultLabels)(implicit
      monad: MonadError[F]
  ): PrometheusMetrics[F] =
    PrometheusMetrics[F](
      namespace,
      defaultRegistry,
      Set(
        requestsTotal(defaultRegistry, namespace, labels),
        requestsActive(defaultRegistry, namespace, labels),
        responsesTotal(defaultRegistry, namespace, labels),
        responsesDuration(defaultRegistry, namespace, labels)
      )
    )

  def requestsTotal[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels)(implicit
      monad: MonadError[F]
  ): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace(namespace)
        .name("requests_total")
        .help("Total HTTP requests")
        .labelNames(labels.forRequestNames: _*)
        .create()
        .register(registry)
    ).onRequest { (ep, req, counter) =>
      monad.unit(counter.labels(labels.forRequest(ep, req): _*).inc())
    }

  def requestsActive[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels)(implicit
      monad: MonadError[F]
  ): Metric[F, Gauge] =
    Metric[F, Gauge](
      Gauge
        .build()
        .namespace(namespace)
        .name("requests_active")
        .help("Active HTTP requests")
        .labelNames(labels.forRequestNames: _*)
        .create()
        .register(registry)
    ).onRequest { (ep, req, gauge) => monad.unit(gauge.labels(labels.forRequest(ep, req): _*).inc()) }
      .onResponse { (ep, req, _, gauge) => monad.unit(gauge.labels(labels.forRequest(ep, req): _*).dec()) }

  def responsesTotal[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels)(implicit
      monad: MonadError[F]
  ): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace(namespace)
        .name("responses_total")
        .help("HTTP responses")
        .labelNames(labels.forRequestNames ++ labels.forResponseNames: _*)
        .register(registry)
    ).onResponse { (ep, req, res, counter) => monad.unit(counter.labels(labels.forRequest(ep, req) ++ labels.forResponse(res): _*).inc()) }

  def responsesDuration[F[_]](registry: CollectorRegistry, namespace: String, labels: PrometheusLabels)(implicit
      monad: MonadError[F]
  ): Metric[F, Histogram] =
    Metric[F, Histogram](
      Histogram
        .build()
        .namespace(namespace)
        .name("responses_duration")
        .help("HTTP responses duration")
        .labelNames(labels.forRequestNames ++ labels.forResponseNames: _*)
        .register(registry)
    ).onResponse { (ep, req, res, histogram) =>
      monad.unit(
        histogram
          .labels(labels.forRequest(ep, req) ++ labels.forResponse(res): _*)
          .observe((Deadline.now - req.requestStart).toMillis.toDouble / 1000.0)
      )
    }

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
