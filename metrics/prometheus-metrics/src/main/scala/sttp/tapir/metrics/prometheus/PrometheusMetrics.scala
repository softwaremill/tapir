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

case class PrometheusMetrics[F[_]](registry: CollectorRegistry, metrics: Set[Metric[F, _]] = Set.empty[Metric[F, _]])(implicit
    monad: MonadError[F]
) {
  import PrometheusMetrics._

  private lazy val metricsEp = endpoint.get.in("metrics").out(plainBody[CollectorRegistry])

  def withRequestsTotal: PrometheusMetrics[F] = copy(metrics = metrics + requestsTotal(registry))
  def withRequestsActive: PrometheusMetrics[F] = copy(metrics = metrics + requestsActive(registry))
  def withRequestsFailure: PrometheusMetrics[F] = copy(metrics = metrics + requestsFailures(registry))
  def withResponsesTotal: PrometheusMetrics[F] = copy(metrics = metrics + responsesTotal(registry))
  def withResponsesDuration: PrometheusMetrics[F] = copy(metrics = metrics + responsesDuration(registry))
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

  def withDefaultMetrics[F[_]](implicit monad: MonadError[F]): PrometheusMetrics[F] =
    PrometheusMetrics[F](
      defaultRegistry,
      Set(
        requestsTotal(defaultRegistry),
        requestsActive(defaultRegistry),
        requestsFailures(defaultRegistry),
        responsesTotal(defaultRegistry),
        responsesDuration(defaultRegistry)
      )
    )

  def requestsTotal[F[_]](registry: CollectorRegistry)(implicit monad: MonadError[F]): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace("tapir")
        .name("requests_total")
        .help("Total HTTP requests")
        .labelNames("path", "method")
        .create()
        .register(registry)
    ).onRequest { (req, counter) => monad.unit(counter.labels(path(req), method(req)).inc()) }

  def requestsActive[F[_]](registry: CollectorRegistry)(implicit monad: MonadError[F]): Metric[F, Gauge] =
    Metric[F, Gauge](
      Gauge
        .build()
        .namespace("tapir")
        .name("requests_active")
        .help("Active HTTP requests")
        .labelNames("path", "method")
        .create()
        .register(registry)
    ).onRequest { (req, gauge) => monad.unit(gauge.labels(path(req), method(req)).inc()) }
      .onResponse { (req, _, gauge) => monad.unit(gauge.labels(path(req), method(req)).dec()) }

  def requestsFailures[F[_]](registry: CollectorRegistry)(implicit monad: MonadError[F]): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace("tapir")
        .name("requests_failure")
        .help("Unserved HTTP requests")
        .labelNames("path", "method")
        .register(registry)
    ).onDecodeFailure { (req, counter) => monad.unit(counter.labels(path(req), method(req)).inc()) }

  def responsesTotal[F[_]](registry: CollectorRegistry)(implicit monad: MonadError[F]): Metric[F, Counter] =
    Metric[F, Counter](
      Counter
        .build()
        .namespace("tapir")
        .name("responses_total")
        .help("HTTP responses")
        .labelNames("path", "method", "status")
        .register(registry)
    ).onResponse { (req, res, counter) => monad.unit(counter.labels(path(req), method(req), status(res)).inc()) }

  def responsesDuration[F[_]](registry: CollectorRegistry)(implicit monad: MonadError[F]): Metric[F, Histogram] =
    Metric[F, Histogram](
      Histogram
        .build()
        .namespace("tapir")
        .name("responses_duration")
        .help("HTTP responses duration")
        .labelNames("path", "method", "status")
        .register(registry)
    ).onResponse { (req, res, histogram) =>
      monad.unit(histogram.labels(path(req), method(req), status(res)).observe((Deadline.now - req.timestamp).toMillis.toDouble / 1000.0))
    }

  private def path(request: ServerRequest): String = request.pathSegments.mkString("/")

  private def method(request: ServerRequest): String = request.method.method

  private def status(response: ServerResponse[_]): String =
    response.code match {
      case c if c.isInformational => "1xx"
      case c if c.isSuccess       => "2xx"
      case c if c.isRedirect      => "3xx"
      case c if c.isClientError   => "4xx"
      case c if c.isServerError   => "5xx"
      case _                      => ""
    }
}
