package sttp.tapir.server.metrics.zio

import sttp.tapir._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric, MetricLabels}
import sttp.tapir.server.model.ServerResponse
import zio._
import zio.metrics.Metric._
import zio.metrics.MetricKeyType.Histogram.Boundaries
import zio.metrics.MetricLabel

import java.time.{Clock, Duration, Instant}

case class ZioMetrics[F[_]](namespace: String, metrics: List[Metric[F, _]] = List.empty) {
  import ZioMetrics._

  /** Registers a `$namespace.request_active.count|c|#path, method` counter (assuming default labels). */
  def addRequestsActive(labels: MetricLabels = MetricLabels.Default): ZioMetrics[F] =
    copy(metrics = metrics :+ requestActive(namespace, labels))

  /** Registers a `request_total{path, method, status}` counter (assuming default labels). */
  def addRequestsTotal(labels: MetricLabels = MetricLabels.Default): ZioMetrics[F] =
    copy(metrics = metrics :+ requestTotal(namespace, labels))

  /** Registers a `request_duration_seconds{path, method, status, phase}` histogram (assuming default labels). */
  def addRequestsDuration(labels: MetricLabels = MetricLabels.Default): ZioMetrics[F] =
    copy(metrics = metrics :+ requestDuration(namespace, labels))

  /** Registers a custom metric. */
  def addCustom(m: Metric[F, _]): ZioMetrics[F] = copy(metrics = metrics :+ m)

  /** A metrics interceptor instance. */
  def metricsInterceptor(ignoreEndpoints: Seq[AnyEndpoint] = Seq.empty): MetricsRequestInterceptor[F] =
    new MetricsRequestInterceptor[F](metrics, ignoreEndpoints)
}

object ZioMetrics {

  /** Default prefix of metric in the zio metric name. */
  val DefaultNamespace: String = "tapir"

  /** Represents general time buckets from .005s to 60s. */
  val DurationBoundaries: Boundaries =
    Boundaries.fromChunk(Chunk(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10, 15, 30, 45, 60))

  /** Using the default namespace and labels, registers the following metrics:
    *
    *   - `tapir_request_active{path, method}` (gauge)
    *   - `tapir_request_total{path, method, status}` (counter)
    *   - `tapir_request_duration_seconds{path, method, status, phase}` (histogram)
    *
    * Status is by default the status code class (1xx, 2xx, etc.), and phase can be either `headers` or `body` - request duration is
    * measured separately up to the point where the headers are determined, and then once again when the whole response body is complete.
    */
  def default[F[_]](namespace: String = DefaultNamespace, labels: MetricLabels = MetricLabels.Default): ZioMetrics[F] = ZioMetrics(
    namespace,
    List(
      requestActive(namespace, labels),
      requestTotal(namespace, labels),
      requestDuration(namespace, labels)
    )
  )

  /** ZIO Default Runtime */
  val runtime: Runtime[Any] = Runtime.default

  /** Active/Inprogress Gauge +1 active, -1 complete. */
  def getActiveRequestGauge(namespace: String): Gauge[Long] =
    zio.metrics.Metric.gauge(s"${namespace}_request_active").contramap(_.toDouble)

  /** Total request counter. */
  def getRequestsTotalCounter(namespace: String): Counter[Long] = zio.metrics.Metric.counter(s"${namespace}_request_total")

  /** Histogram buckets in seconds. */
  def getRequestDurationHistogram(namespace: String): zio.metrics.Metric.Histogram[Double] =
    zio.metrics.Metric.histogram(s"${namespace}_request_duration_seconds", DurationBoundaries)

  /** ZIO Unsafe Run Wrapper */
  private def unsafeRun[T](effect: UIO[T]): T = Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(effect).getOrThrowFiberFailure()
  }

  /** Convert into zio metric labels */
  private def asZioLabel(l: MetricLabels, ep: AnyEndpoint, req: ServerRequest): Set[MetricLabel] =
    l.forRequest.map(label => zio.metrics.MetricLabel(label._1, label._2(ep, req))).toSet

  /** Convert into zio metric labels */
  private def asZioLabel(l: MetricLabels, res: Either[Throwable, ServerResponse[_]], phase: Option[String]): Set[MetricLabel] = {
    val responseLabels = l.forResponse.map { case (key, valueFn) =>
      MetricLabel(key, valueFn(res).getOrElse("unknown"))
    }
    val phaseLabel = phase.map(v => MetricLabel(l.forResponsePhase.name, v))
    (responseLabels ++ phaseLabel).toSet
  }

  /** Requests active metric collector. */
  def requestActive[F[_]](namespace: String, labels: MetricLabels): Metric[F, Gauge[Long]] = {
    Metric[F, Gauge[Long]](
      getActiveRequestGauge(namespace),
      onRequest = (req, gauge, m) => {
        m.unit {
          EndpointMetric()
            .onEndpointRequest { ep =>
              m.eval {
                unsafeRun(
                  gauge.tagged(asZioLabel(labels, ep, req)).increment
                )
              }
            }
            .onResponseBody { (ep, _) =>
              m.eval {
                unsafeRun(
                  gauge.tagged(asZioLabel(labels, ep, req)).decrement
                )
              }
            }
            .onException { (ep, _) =>
              m.eval {
                unsafeRun(
                  gauge.tagged(asZioLabel(labels, ep, req)).decrement
                )
              }
            }
        }
      }
    )
  }

  /** Requests total metric collector. */
  def requestTotal[F[_]](namespace: String, labels: MetricLabels): Metric[F, Counter[Long]] = {
    Metric[F, Counter[Long]](
      getRequestsTotalCounter(namespace),
      onRequest = { (req, counter, m) =>
        m.unit {
          EndpointMetric()
            .onResponseBody { (ep, res) =>
              m.eval {
                unsafeRun(
                  counter.tagged(asZioLabel(labels, ep, req) ++ asZioLabel(labels, Right(res), None)).increment
                )
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                unsafeRun(
                  counter.tagged(asZioLabel(labels, ep, req) ++ asZioLabel(labels, Left(ex), None)).increment
                )
              }
            }
        }
      }
    )
  }

  /** Request duration metric collector. */
  def requestDuration[F[_]](
      namespace: String,
      labels: MetricLabels,
      clock: Clock = Clock.systemUTC()
  ): Metric[F, zio.metrics.Metric.Histogram[Double]] =
    Metric[F, zio.metrics.Metric.Histogram[Double]](
      getRequestDurationHistogram(namespace),
      onRequest = { (req, histogram, m) =>
        m.eval {
          val requestStart: Instant = clock.instant()
          def duration = Duration.between(requestStart, clock.instant()).toNanos.toDouble / (1000 * 1000 * 1000)
          EndpointMetric()
            .onResponseHeaders { (ep, res) =>
              m.eval {
                unsafeRun(
                  histogram
                    .tagged(asZioLabel(labels, ep, req) ++ asZioLabel(labels, Right(res), Some(labels.forResponsePhase.headersValue)))
                    .update(duration)
                )
              }
            }
            .onResponseBody { (ep, res) =>
              m.eval {
                unsafeRun(
                  histogram
                    .tagged(asZioLabel(labels, ep, req) ++ asZioLabel(labels, Right(res), Some(labels.forResponsePhase.bodyValue)))
                    .update(duration)
                )
              }
            }
            .onException { (ep: AnyEndpoint, ex: Throwable) =>
              m.eval {
                unsafeRun(
                  histogram.tagged(asZioLabel(labels, ep, req) ++ asZioLabel(labels, Left(ex), None)).update(duration)
                )
              }
            }
        }
      }
    )
}
