package sttp.tapir.server.metrics.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{DoubleHistogram, LongCounter, LongUpDownCounter, Meter}
import sttp.tapir.AnyEndpoint
import OpenTelemetryMetrics._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.{EndpointMetric, Metric, MetricLabels}
import sttp.tapir.server.model.ServerResponse

import java.time.{Duration, Instant}

case class OpenTelemetryMetrics[F[_]](meter: Meter, metrics: List[Metric[F, _]] = List.empty[Metric[F, _]]) {

  def withRequestsTotal(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ requestsTotal(meter, labels))
  def withRequestsActive(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ requestsActive(meter, labels))
  def withResponsesTotal(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ responsesTotal(meter, labels))
  def withResponsesDuration(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ responsesDuration(meter, labels))
  def withCustom(m: Metric[F, _]): OpenTelemetryMetrics[F] = copy(metrics = metrics :+ m)

  def metricsInterceptor[B](ignoreEndpoints: Seq[AnyEndpoint] = Seq.empty): MetricsRequestInterceptor[F] =
    new MetricsRequestInterceptor[F](metrics, ignoreEndpoints)
}

object OpenTelemetryMetrics {

  def withDefaultMetrics[F[_]](meter: Meter, labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    OpenTelemetryMetrics(
      meter,
      List(
        requestsTotal(meter, labels),
        requestsActive(meter, labels),
        responsesTotal(meter, labels),
        responsesDuration(meter, labels)
      )
    )

  def requestsTotal[F[_]](meter: Meter, labels: MetricLabels): Metric[F, LongCounter] =
    Metric[F, LongCounter](
      meter
        .counterBuilder("requests_total")
        .setDescription("Total HTTP requests")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric().onEndpointRequest { ep =>
            m.eval(counter.add(1, asOpenTelemetryAttributes(labels, ep, req)))
          }
        }
      }
    )

  def requestsActive[F[_]](meter: Meter, labels: MetricLabels): Metric[F, LongUpDownCounter] =
    Metric[F, LongUpDownCounter](
      meter
        .upDownCounterBuilder("requests_active")
        .setDescription("Active HTTP requests")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric()
            .onEndpointRequest { ep => m.eval(counter.add(1, asOpenTelemetryAttributes(labels, ep, req))) }
            .onResponse { (ep, _) => m.eval(counter.add(-1, asOpenTelemetryAttributes(labels, ep, req))) }
            .onException { (ep, _) => m.eval(counter.add(-1, asOpenTelemetryAttributes(labels, ep, req))) }
        }
      }
    )

  def responsesTotal[F[_]](meter: Meter, labels: MetricLabels): Metric[F, LongCounter] =
    Metric[F, LongCounter](
      meter
        .counterBuilder("responses_total")
        .setDescription("Total HTTP responses")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric()
            .onResponse { (ep, res) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryAttributes(labels, ep, req), asOpenTelemetryAttributes(labels, Right(res)))
                counter.add(1, otLabels)
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryAttributes(labels, ep, req), asOpenTelemetryAttributes(labels, Left(ex)))
                counter.add(1, otLabels)
              }
            }
        }
      }
    )

  def responsesDuration[F[_]](meter: Meter, labels: MetricLabels): Metric[F, DoubleHistogram] =
    Metric[F, DoubleHistogram](
      meter
        .histogramBuilder("responses_duration")
        .setDescription("HTTP responses duration")
        .setUnit("ms")
        .build(),
      onRequest = (req, recorder, m) =>
        m.unit {
          val requestStart = Instant.now()
          EndpointMetric()
            .onResponse { (ep, res) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryAttributes(labels, ep, req), asOpenTelemetryAttributes(labels, Right(res)))
                recorder.record(Duration.between(requestStart, Instant.now()).toMillis.toDouble, otLabels)
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryAttributes(labels, ep, req), asOpenTelemetryAttributes(labels, Left(ex)))
                recorder.record(Duration.between(requestStart, Instant.now()).toMillis.toDouble, otLabels)
              }
            }
        }
    )

  private def asOpenTelemetryAttributes(l: MetricLabels, ep: AnyEndpoint, req: ServerRequest): Attributes =
    l.forRequest.foldLeft(Attributes.builder())((b, label) => { b.put(label._1, label._2(ep, req)) }).build()

  private def asOpenTelemetryAttributes(l: MetricLabels, res: Either[Throwable, ServerResponse[_]]): Attributes =
    l.forResponse.foldLeft(Attributes.builder())((b, label) => { b.put(label._1, label._2(res)) }).build()

  private def merge(a1: Attributes, a2: Attributes): Attributes = a1.toBuilder.putAll(a2).build()
}
