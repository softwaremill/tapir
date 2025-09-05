package sttp.tapir.server.metrics.otel4s

import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.metrics.{Counter, Histogram, Meter}
import org.typelevel.otel4s.semconv.attributes.{ErrorAttributes, HttpAttributes, UrlAttributes}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric, MetricLabelsTyped}
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.model.ServerResponse

import java.time.{Duration, Instant}

case class Otel4sMetrics[F[_]](metrics: List[Metric[F, _]]) {

  import Otel4sMetrics._

  /** Registers a `http.server.active_requests` up-down-counter (assuming default labels). */
  def addRequestsActive(meter: Meter[F], labels: MetricLabels = DefaultMetricLabels): Otel4sMetrics[F] =
    copy(metrics = metrics :+ requestActive(meter, labels))

  /** Registers a `http.server.requests.total` counter (assuming default labels). */
  def addRequestsTotal(meter: Meter[F], labels: MetricLabels = DefaultMetricLabels): Otel4sMetrics[F] =
    copy(metrics = metrics :+ requestTotal(meter, labels))

  /** Registers a `http.server.request.duration` histogram (assuming default labels). */
  def addRequestsDuration(meter: Meter[F], labels: MetricLabels = DefaultMetricLabels): Otel4sMetrics[F] =
    copy(metrics = metrics :+ requestDuration(meter, labels))

  /** Registers a custom metric. */
  def addCustom(m: Metric[F, _]): Otel4sMetrics[F] = copy(metrics = metrics :+ m)

  /** The interceptor which can be added to a server's options, to enable metrics collection. */
  def metricsInterceptor(ignoreEndpoints: Seq[AnyEndpoint] = Seq.empty): MetricsRequestInterceptor[F] =
    new MetricsRequestInterceptor[F](metrics, ignoreEndpoints)
}

object Otel4sMetrics {
  private type MetricLabels = MetricLabelsTyped[Attribute[_]]

  /** Using the default labels, registers the following metrics:
    *
    *   - `http.server.active_requests` (up-down-counter)
    *   - `http.server.requests.total` (counter)
    *   - `http.server.request.duration` (histogram)
    */
  def default[F[_]](meter: Meter[F], labels: MetricLabels = DefaultMetricLabels): Otel4sMetrics[F] =
    Otel4sMetrics(
      List[Metric[F, _]](
        requestActive(meter, labels),
        requestTotal(meter, labels),
        requestDuration(meter, labels)
      )
    )

  /** Default labels for OpenTelemetry-compliant metrics, as recommended here:
    * https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-server
    *
    *   - `http.request.method` - HTTP request method (e.g., GET, POST).
    *   - `url.scheme` - the scheme of the request URL (e.g., http, https).
    *   - `http.route` - the request path or route template.
    *   - `http.response.status_code` - HTTP response status code (200, 404, etc.).
    */
  private val DefaultMetricLabels: MetricLabels = MetricLabelsTyped[Attribute[_]](
    forRequest = List(
      { case (ep, req) => HttpAttributes.HttpRequestMethod(req.method.method) },
      { case (_, req) => UrlAttributes.UrlScheme(req.uri.scheme.getOrElse("unknown")) },
      { case (ep, _) => HttpAttributes.HttpRoute(ep.showPathTemplate(showQueryParam = None)) }
    ),
    forResponse = List(
      {
        case Right(r) => Some(HttpAttributes.HttpResponseStatusCode(r.code.code.toLong))
        case Left(ex) => Some(HttpAttributes.HttpResponseStatusCode(500))
      },
      {
        case Right(_) => None
        case Left(ex) => Some(ErrorAttributes.ErrorType(ex.getClass.getName))
      }
    )
  )

  private def requestActive[F[_]](meter: Meter[F], labels: MetricLabels): Metric[F, ?] =
    Metric(
      metric = meter
        .upDownCounter[Long]("http.server.active_requests")
        .withDescription("Active HTTP requests")
        .withUnit("1")
        .create,
      onRequest = (req, counterM, m) =>
        m.map(counterM) { counter =>
          EndpointMetric()
            .onEndpointRequest(ep => counter.inc(requestAttrs(labels, ep, req)))
            .onResponseBody((ep, _) => counter.dec(requestAttrs(labels, ep, req)))
            .onException((ep, _) => counter.dec(requestAttrs(labels, ep, req)))
        }
    )

  private def requestTotal[F[_]](meter: Meter[F], labels: MetricLabels): Metric[F, F[Counter[F, Long]]] =
    Metric(
      metric = meter
        .counter[Long]("http.server.requests.total")
        .withDescription("Total HTTP requests")
        .withUnit("1")
        .create,
      onRequest = (req, counterM, m) =>
        m.map(counterM) { counter =>
          EndpointMetric()
            .onResponseBody { (ep, res) =>
              counter.inc(requestAttrs(labels, ep, req) ++ responseAttrs(labels, Right(res), None))
            }
            .onException { (ep, ex) =>
              counter.inc(requestAttrs(labels, ep, req) ++ responseAttrs(labels, Left(ex), None))
            }
        }
    )

  private def requestDuration[F[_]](meter: Meter[F], labels: MetricLabels): Metric[F, F[Histogram[F, Double]]] =
    Metric(
      metric = meter
        .histogram[Double]("http.server.request.duration")
        .withDescription("Duration of HTTP requests")
        .withUnit("ms")
        .create,
      onRequest = (req, recorderM, m) =>
        m.map(recorderM) { recorder =>
          val requestStart = Instant.now()

          def duration = Duration.between(requestStart, Instant.now()).toMillis.toDouble

          EndpointMetric()
            .onResponseHeaders { (ep, res) =>
              recorder.record(
                duration,
                requestAttrs(labels, ep, req) ++ responseAttrs(labels, Right(res), Some(labels.forResponsePhase.headersValue))
              )
            }
            .onResponseBody { (ep, res) =>
              recorder.record(
                duration,
                requestAttrs(labels, ep, req) ++ responseAttrs(labels, Right(res), Some(labels.forResponsePhase.bodyValue))
              )
            }
            .onException { (ep, ex) =>
              recorder.record(
                duration,
                requestAttrs(labels, ep, req) ++ responseAttrs(labels, Left(ex), None)
              )
            }
        }
    )

  private[otel4s] def requestAttrs(l: MetricLabels, ep: AnyEndpoint, req: ServerRequest): Attributes =
    Attributes.newBuilder
      .addAll(l.forRequest.map(label => label(ep, req)))
      .result()

  private[otel4s] def responseAttrs(l: MetricLabels, res: Either[Throwable, ServerResponse[_]], phase: Option[String]): Attributes =
    Attributes.newBuilder
      .addAll(l.forResponse.flatMap(label => label(res)))
      .addAll(phase.map(v => Attribute.from(l.forResponsePhase.name, v)))
      .result()
}
