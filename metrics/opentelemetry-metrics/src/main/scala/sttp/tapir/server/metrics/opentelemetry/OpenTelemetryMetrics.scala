package sttp.tapir.server.metrics.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{DoubleHistogram, LongCounter, LongUpDownCounter, Meter}
import sttp.tapir.AnyEndpoint
import OpenTelemetryMetrics._
import io.opentelemetry.api.OpenTelemetry
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric, MetricLabels}
import sttp.tapir.server.model.ServerResponse

import java.time.{Duration, Instant}

case class OpenTelemetryMetrics[F[_]](meter: Meter, metrics: List[Metric[F, _]]) {

  /** Registers a `request_active{path, method}` up-down-counter (assuming default labels). */
  def addRequestsActive(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ requestActive(meter, labels))

  /** Registers a `request_total{path, method, status}` counter (assuming default labels). */
  def addRequestsTotal(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ requestTotal(meter, labels))

  /** Registers a `request_duration_seconds{path, method, status, phase}` histogram (assuming default labels). */
  def addRequestsDuration(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ requestDuration(meter, labels))

  /** Registers a custom metric. */
  def addCustom(m: Metric[F, _]): OpenTelemetryMetrics[F] = copy(metrics = metrics :+ m)

  /** The interceptor which can be added to a server's options, to enable metrics collection. */
  def metricsInterceptor(ignoreEndpoints: Seq[AnyEndpoint] = Seq.empty): MetricsRequestInterceptor[F] =
    new MetricsRequestInterceptor[F](metrics, ignoreEndpoints)
}

object OpenTelemetryMetrics {

  def apply[F[_]](meter: Meter): OpenTelemetryMetrics[F] = apply(meter, Nil)
  def apply[F[_]](otel: OpenTelemetry): OpenTelemetryMetrics[F] = apply(defaultMeter(otel), Nil)
  def apply[F[_]](otel: OpenTelemetry, metrics: List[Metric[F, _]]): OpenTelemetryMetrics[F] = apply(defaultMeter(otel), metrics)

  /** Using the default labels, registers the following metrics:
    *
    *   - `request_active{path, method}` (up-down-counter)
    *   - `request_total{path, method, status}` (counter)
    *   - `request_duration{path, method, status, phase}` (histogram)
    *
    * Status is by default the status code class (1xx, 2xx, etc.), and phase can be either `headers` or `body` - request duration is
    * measured separately up to the point where the headers are determined, and then once again when the whole response body is complete.
    */
  def default[F[_]](otel: OpenTelemetry): OpenTelemetryMetrics[F] =
    default(defaultMeter(otel), MetricLabels.Default)

  /** Registers default metrics (see other variants) using custom labels. */
  def default[F[_]](otel: OpenTelemetry, labels: MetricLabels): OpenTelemetryMetrics[F] = default(defaultMeter(otel), labels)

  /** Using the default labels, registers the following metrics:
    *
    *   - `request_active{path, method}` (up-down-counter)
    *   - `request_total{path, method, status}` (counter)
    *   - `request_duration{path, method, status, phase}` (histogram)
    *
    * Status is by default the status code class (1xx, 2xx, etc.), and phase can be either `headers` or `body` - request duration is
    * measured separately up to the point where the headers are determined, and then once again when the whole response body is complete.
    */
  def default[F[_]](meter: Meter): OpenTelemetryMetrics[F] = default(meter, MetricLabels.Default)

  /** Registers default metrics (see other variants) using custom labels. */
  def default[F[_]](meter: Meter, labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    OpenTelemetryMetrics(
      meter,
      List[Metric[F, _]](
        requestActive(meter, labels),
        requestTotal(meter, labels),
        requestDuration(meter, labels)
      )
    )

  def requestActive[F[_]](meter: Meter, labels: MetricLabels): Metric[F, LongUpDownCounter] =
    Metric[F, LongUpDownCounter](
      meter
        .upDownCounterBuilder("request_active")
        .setDescription("Active HTTP requests")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric()
            .onEndpointRequest { ep => m.eval(counter.add(1, asOpenTelemetryAttributes(labels, ep, req))) }
            .onResponseBody { (ep, _) => m.eval(counter.add(-1, asOpenTelemetryAttributes(labels, ep, req))) }
            .onException { (ep, _) => m.eval(counter.add(-1, asOpenTelemetryAttributes(labels, ep, req))) }
        }
      }
    )

  def requestTotal[F[_]](meter: Meter, labels: MetricLabels): Metric[F, LongCounter] =
    Metric[F, LongCounter](
      meter
        .counterBuilder("request_total")
        .setDescription("Total HTTP requests")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric()
            .onResponseBody { (ep, res) =>
              m.eval {
                val otLabels =
                  merge(asOpenTelemetryAttributes(labels, ep, req), asOpenTelemetryAttributes(labels, Right(res), None))
                counter.add(1, otLabels)
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                val otLabels =
                  merge(asOpenTelemetryAttributes(labels, ep, req), asOpenTelemetryAttributes(labels, Left(ex), None))
                counter.add(1, otLabels)
              }
            }
        }
      }
    )

  def requestDuration[F[_]](meter: Meter, labels: MetricLabels): Metric[F, DoubleHistogram] =
    Metric[F, DoubleHistogram](
      meter
        .histogramBuilder("request_duration")
        .setDescription("Duration of HTTP requests")
        .setUnit("ms")
        .build(),
      onRequest = (req, recorder, m) =>
        m.eval {
          val requestStart = Instant.now()
          def duration = Duration.between(requestStart, Instant.now()).toMillis.toDouble
          EndpointMetric()
            .onResponseHeaders { (ep, res) =>
              m.eval {
                val otLabels =
                  merge(
                    asOpenTelemetryAttributes(labels, ep, req),
                    asOpenTelemetryAttributes(labels, Right(res), Some(labels.forResponsePhase.headersValue))
                  )
                recorder.record(duration, otLabels)
              }
            }
            .onResponseBody { (ep, res) =>
              m.eval {
                val otLabels =
                  merge(
                    asOpenTelemetryAttributes(labels, ep, req),
                    asOpenTelemetryAttributes(labels, Right(res), Some(labels.forResponsePhase.bodyValue))
                  )
                recorder.record(duration, otLabels)
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                val otLabels =
                  merge(asOpenTelemetryAttributes(labels, ep, req), asOpenTelemetryAttributes(labels, Left(ex), None))
                recorder.record(duration, otLabels)
              }
            }
        }
    )

  private def defaultMeter(otel: OpenTelemetry): Meter = otel.meterBuilder("tapir").setInstrumentationVersion("1.0.0").build()

  private def asOpenTelemetryAttributes(l: MetricLabels, ep: AnyEndpoint, req: ServerRequest): Attributes =
    l.forRequest.foldLeft(Attributes.builder())((b, label) => { b.put(label._1, label._2(ep, req)) }).build()

  private def asOpenTelemetryAttributes(l: MetricLabels, res: Either[Throwable, ServerResponse[_]], phase: Option[String]): Attributes = {
    val builder = l.forResponse.foldLeft(Attributes.builder())((b, label) => { b.put(label._1, label._2(res)) })
    phase.foreach(v => builder.put(l.forResponsePhase.name, v))
    builder.build()
  }

  private def merge(a1: Attributes, a2: Attributes): Attributes = a1.toBuilder.putAll(a2).build()
}
