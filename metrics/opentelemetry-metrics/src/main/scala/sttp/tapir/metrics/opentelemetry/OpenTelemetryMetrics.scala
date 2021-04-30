package sttp.tapir.metrics.opentelemetry

import io.opentelemetry.api.metrics.common.Labels
import io.opentelemetry.api.metrics.{DoubleValueRecorder, LongCounter, LongUpDownCounter, MeterProvider}
import sttp.tapir.Endpoint
import sttp.tapir.metrics.opentelemetry.OpenTelemetryMetrics._
import sttp.tapir.metrics.{EndpointMetric, Metric, MetricLabels}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

import java.time.{Duration, Instant}

case class OpenTelemetryMetrics[F[_]](
    provider: MeterProvider,
    instrumentationName: String,
    instrumentationVersion: String,
    metrics: List[Metric[F, _]] = List.empty[Metric[F, _]]
) {

  def withRequestsTotal(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ requestsTotal(instrumentationName, instrumentationVersion, provider, labels))
  def withRequestsActive(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ requestsActive(instrumentationName, instrumentationVersion, provider, labels))
  def withResponsesTotal(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ responsesTotal(instrumentationName, instrumentationVersion, provider, labels))
  def withResponsesDuration(labels: MetricLabels = MetricLabels.Default): OpenTelemetryMetrics[F] =
    copy(metrics = metrics :+ responsesDuration(instrumentationName, instrumentationVersion, provider, labels))
  def withCustom(m: Metric[F, _]): OpenTelemetryMetrics[F] = copy(metrics = metrics :+ m)

  def metricsInterceptor[B](ignoreEndpoints: Seq[Endpoint[_, _, _, _]] = Seq.empty): MetricsRequestInterceptor[F, B] =
    new MetricsRequestInterceptor[F, B](metrics, ignoreEndpoints)
}

object OpenTelemetryMetrics {

  def withDefaultMetrics[F[_]](
      provider: MeterProvider,
      instrumentationName: String = "opentelemetry-instrumentation-tapir",
      instrumentationVersion: String = "1.0.0",
      labels: MetricLabels = MetricLabels.Default
  ): OpenTelemetryMetrics[F] =
    OpenTelemetryMetrics(
      provider,
      instrumentationName,
      instrumentationVersion,
      List(
        requestsTotal(instrumentationName, instrumentationVersion, provider, labels),
        requestsActive(instrumentationName, instrumentationVersion, provider, labels),
        responsesTotal(instrumentationName, instrumentationVersion, provider, labels),
        responsesDuration(instrumentationName, instrumentationVersion, provider, labels)
      )
    )

  def requestsTotal[F[_]](
      instrumentationName: String,
      instrumentationVersion: String,
      provider: MeterProvider,
      labels: MetricLabels
  ): Metric[F, LongCounter] =
    Metric[F, LongCounter](
      provider
        .get(instrumentationName, instrumentationVersion)
        .longCounterBuilder("requests_total")
        .setDescription("Total HTTP requests")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric().onEndpointRequest { ep =>
            m.eval(counter.add(1, asOpenTelemetryLabels(labels, ep, req)))
          }
        }
      }
    )

  def requestsActive[F[_]](
      instrumentationName: String,
      instrumentationVersion: String,
      provider: MeterProvider,
      labels: MetricLabels
  ): Metric[F, LongUpDownCounter] =
    Metric[F, LongUpDownCounter](
      provider
        .get(instrumentationName, instrumentationVersion)
        .longUpDownCounterBuilder("requests_active")
        .setDescription("Active HTTP requests")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric()
            .onEndpointRequest { ep => m.eval(counter.add(1, asOpenTelemetryLabels(labels, ep, req))) }
            .onResponse { (ep, _) => m.eval(counter.add(-1, asOpenTelemetryLabels(labels, ep, req))) }
            .onException { (ep, _) => m.eval(counter.add(-1, asOpenTelemetryLabels(labels, ep, req))) }
        }
      }
    )

  def responsesTotal[F[_]](
      instrumentationName: String,
      instrumentationVersion: String,
      provider: MeterProvider,
      labels: MetricLabels
  ): Metric[F, LongCounter] =
    Metric[F, LongCounter](
      provider
        .get(instrumentationName, instrumentationVersion)
        .longCounterBuilder("responses_total")
        .setDescription("Total HTTP responses")
        .setUnit("1")
        .build(),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric()
            .onResponse { (ep, res) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryLabels(labels, ep, req), asOpenTelemetryLabels(labels, Right(res)))
                counter.add(1, otLabels)
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryLabels(labels, ep, req), asOpenTelemetryLabels(labels, Left(ex)))
                counter.add(1, otLabels)
              }
            }
        }
      }
    )

  def responsesDuration[F[_]](
      instrumentationName: String,
      instrumentationVersion: String,
      provider: MeterProvider,
      labels: MetricLabels
  ): Metric[F, DoubleValueRecorder] =
    Metric[F, DoubleValueRecorder](
      provider
        .get(instrumentationName, instrumentationVersion)
        .doubleValueRecorderBuilder("responses_duration")
        .setDescription("HTTP responses duration")
        .setUnit("ms")
        .build(),
      onRequest = (req, recorder, m) =>
        m.unit {
          val requestStart = Instant.now()
          EndpointMetric()
            .onResponse { (ep, res) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryLabels(labels, ep, req), asOpenTelemetryLabels(labels, Right(res)))
                recorder.record(Duration.between(requestStart, Instant.now()).toMillis.toDouble, otLabels)
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                val otLabels = merge(asOpenTelemetryLabels(labels, ep, req), asOpenTelemetryLabels(labels, Left(ex)))
                recorder.record(Duration.between(requestStart, Instant.now()).toMillis.toDouble, otLabels)
              }
            }
        }
    )

  private def asOpenTelemetryLabels(l: MetricLabels, ep: Endpoint[_, _, _, _], req: ServerRequest): Labels =
    l.forRequest.foldLeft(Labels.builder())((b, label) => { b.put(label._1, label._2(ep, req)) }).build()

  private def asOpenTelemetryLabels(l: MetricLabels, res: Either[Throwable, ServerResponse[_]]): Labels =
    l.forResponse.foldLeft(Labels.builder())((b, label) => { b.put(label._1, label._2(res)) }).build()

  private def merge(l1: Labels, l2: Labels): Labels = {
    val b = Labels.builder()
    l1.forEach((n, v) => b.put(n, v))
    l2.forEach((n, v) => b.put(n, v))
    b.build()
  }
}
