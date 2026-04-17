package sttp.tapir.server.metrics.datadog

import com.timgroup.statsd.StatsDClient
import sttp.tapir._
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics._

import java.time.{Clock, Duration}

case class DatadogMetrics[F[_]](
    client: StatsDClient,
    namespace: String = "tapir",
    metrics: List[Metric[F, _]] = Nil
) {
  import DatadogMetrics._

  /** Registers a `$namespace.request_active.count|c|#path, method` counter (assuming default labels). */
  def addRequestsActive(labels: MetricLabels = MetricLabels.Default): DatadogMetrics[F] =
    copy(metrics = metrics :+ requestActive(client, namespace, labels))

  /** Registers a `$namespace.request_total.count|c|#path, method, status` counter (assuming default labels). */
  def addRequestsTotal(labels: MetricLabels = MetricLabels.Default): DatadogMetrics[F] =
    copy(metrics = metrics :+ requestTotal(client, namespace, labels))

  /** Registers a `$namespace.request_duration_seconds.xxx|h|#path, method, status, phase` histogram (assuming default labels). */
  def addRequestsDuration(labels: MetricLabels = MetricLabels.Default, clock: Clock = Clock.systemUTC()): DatadogMetrics[F] =
    copy(metrics = metrics :+ requestDuration(client, namespace, labels, clock))

  /** Registers a custom metric. */
  def addCustom(m: Metric[F, _]): DatadogMetrics[F] = copy(metrics = metrics :+ m)

  /** The interceptor which can be added to a server's options, to enable metrics collection. */
  def metricsInterceptor(ignoreEndpoints: Seq[AnyEndpoint] = Seq.empty): MetricsRequestInterceptor[F] =
    new MetricsRequestInterceptor[F](metrics, ignoreEndpoints)
}

object DatadogMetrics {

  /** Using the default namespace and labels, registers the following metrics:
    *
    *   - `$namespace.request_active.count|c|#path, method` (counter)
    *   - `$namespace.request_total.count|c|#path, method, status}` (counter)
    *   - `$namespace.request_duration_seconds.xxx|h|#path, method, status, phase}` (histogram)
    *
    * Status is by default the status code class (1xx, 2xx, etc.), and phase can be either `headers` or `body` - request duration is
    * measured separately up to the point where the headers are determined, and then once again when the whole response body is complete.
    */
  def default[F[_]](
      client: StatsDClient,
      namespace: String = "tapir",
      labels: MetricLabels = MetricLabels.Default
  ): DatadogMetrics[F] =
    DatadogMetrics(
      client,
      namespace,
      List(
        requestActive(client, namespace, labels),
        requestTotal(client, namespace, labels),
        requestDuration(client, namespace, labels)
      )
    )

  def requestActive[F[_]](client: StatsDClient, namespace: String, labels: MetricLabels): Metric[F, Counter] =
    Metric[F, Counter](
      Counter(
        (if (namespace.isBlank) "" else namespace + ".") + "request_active.count"
      )(client),
      onRequest = (req, counter, m) => {
        val tags = mergeTags(labels.namesForRequest, labels.valuesForRequest(req))
        m.map(m.eval(counter.increment(tags))) { _ =>
          EndpointMetric()
            .onResponseBody { (_, _) => m.eval(counter.decrement(tags)) }
            .onException { (_, _) => m.eval(counter.decrement(tags)) }
            .onInterceptorResponse { _ => m.eval(counter.decrement(tags)) }
            .onDecodeFailure { () => m.eval(counter.decrement(tags)) }
        }
      }
    )

  def requestTotal[F[_]](client: StatsDClient, namespace: String, labels: MetricLabels): Metric[F, Counter] =
    Metric[F, Counter](
      Counter(
        (if (namespace.isBlank) "" else namespace + ".") + "request_total.count"
      )(client),
      onRequest = (req, counter, m) => {
        m.unit {
          EndpointMetric()
            .onResponseBody { (ep, res) =>
              m.eval {
                counter.increment(
                  mergeTags(
                    labels.namesForRequest ++ labels.namesForEndpoint ++ labels.namesForResponse,
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(res)
                  )
                )
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                counter.increment(
                  mergeTags(
                    labels.namesForRequest ++ labels.namesForEndpoint ++ labels.namesForResponse,
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(ex)
                  )
                )
              }
            }
            .onInterceptorResponse { res =>
              m.eval {
                counter.increment(
                  mergeTags(
                    labels.namesForRequest ++ labels.namesForResponse,
                    labels.valuesForRequest(req) ++ labels.valuesForResponse(res)
                  )
                )
              }
            }
        }
      }
    )

  def requestDuration[F[_]](
      client: StatsDClient,
      namespace: String,
      labels: MetricLabels,
      clock: Clock = Clock.systemUTC()
  ): Metric[F, Histogram] =
    Metric[F, Histogram](
      Histogram(
        (if (namespace.isBlank) "" else namespace + ".") + "request_duration_seconds"
      )(client),
      onRequest = (req, recoder, m) => {
        m.eval {
          val requestStart = clock.instant()
          def duration = Duration.between(requestStart, clock.instant()).toMillis.toDouble / 1000.0
          EndpointMetric()
            .onResponseHeaders { (ep, res) =>
              m.eval {
                recoder.record(
                  duration,
                  mergeTags(
                    labels.namesForRequest ++ labels.namesForEndpoint ++ labels.namesForResponse ++ List(labels.forResponsePhase.name),
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(res) ++ List(
                      labels.forResponsePhase.headersValue
                    )
                  )
                )
              }
            }
            .onResponseBody { (ep, res) =>
              m.eval {
                recoder.record(
                  duration,
                  mergeTags(
                    labels.namesForRequest ++ labels.namesForEndpoint ++ labels.namesForResponse ++ List(labels.forResponsePhase.name),
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(res) ++ List(
                      labels.forResponsePhase.bodyValue
                    )
                  )
                )
              }
            }
            .onException { (ep, ex) =>
              m.eval {
                recoder.record(
                  duration,
                  mergeTags(
                    labels.namesForRequest ++ labels.namesForEndpoint ++ labels.namesForResponse ++ List(labels.forResponsePhase.name),
                    labels.valuesForRequest(req) ++ labels.valuesForEndpoint(ep) ++ labels.valuesForResponse(ex) ++ List(
                      labels.forResponsePhase.bodyValue
                    )
                  )
                )
              }
            }
            .onInterceptorResponse { res =>
              m.eval {
                recoder.record(
                  duration,
                  mergeTags(
                    labels.namesForRequest ++ labels.namesForResponse ++ List(labels.forResponsePhase.name),
                    labels.valuesForRequest(req) ++ labels.valuesForResponse(res) ++ List(labels.forResponsePhase.bodyValue)
                  )
                )
              }
            }
        }
      }
    )

  case class Counter(name: String)(client: StatsDClient) {
    def delta(delta: Long, tags: List[String]): Unit = client.count(name, delta, tags: _*)
    def delta(delta: Double, tags: List[String]): Unit = client.count(name, delta, tags: _*)
    def increment(tags: List[String]): Unit = client.incrementCounter(name, tags: _*)
    def decrement(tags: List[String]): Unit = client.decrementCounter(name, tags: _*)
  }

  case class Gauge(name: String)(client: StatsDClient) {
    def record(value: Long, tags: List[String]): Unit = client.recordGaugeValue(name, value, tags: _*)
    def record(value: Double, tags: List[String]): Unit = client.recordGaugeValue(name, value, tags: _*)
  }

  case class Timer(name: String)(client: StatsDClient) {
    def record(milliSeconds: Long, tags: List[String]): Unit = client.recordExecutionTime(name, milliSeconds, tags: _*)
  }

  case class Histogram(name: String)(client: StatsDClient) {
    def record(value: Long, tags: List[String]): Unit = client.recordHistogramValue(name, value, tags: _*)
    def record(value: Double, tags: List[String]): Unit = client.recordHistogramValue(name, value, tags: _*)
  }

  case class Distribution(name: String)(client: StatsDClient) {
    def record(value: Long, tags: List[String]): Unit = client.recordDistributionValue(name, value, tags: _*)
    def record(value: Double, tags: List[String]): Unit = client.recordDistributionValue(name, value, tags: _*)
  }

  def mergeTags(names: List[String], values: List[String]): List[String] = names.zip(values).map { case (n, v) =>
    n + ":" + v
  }
}
