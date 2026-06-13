package sttp.tapir.server.ziopentelemetry

import zio._
import io.opentelemetry.api
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.AggregationTemporality

import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.`export`.AggregationTemporalitySelector
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.opentelemetry.OpenTelemetryMetrics

import zio.telemetry.opentelemetry.metrics.Meter
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder
import io.opentelemetry.sdk.metrics.`export`.MetricReader
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReaderBuilder
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder

/** Provides metrics for the application.
  *
  * This trait is used to provide metrics for the application. It is used by the [[ZIOpenTelemetryApp]] trait.
  */
trait Metrics {
  this: ZIOpenTelemetryApp =>

  /** Whether to collect ZIO metrics. Defaults to true. */
  def collectZioMetrics: Boolean = true

  /** Allows to customize the OTLP gRPC metric exporter. */
  def customMetricExporter(exporter: OtlpGrpcMetricExporterBuilder): OtlpGrpcMetricExporterBuilder = exporter

  /** Provides a meter provider for OpenTelemetry, which exports metrics in OTLP gRPC format with [[MeterProvider]]
    *
    * If `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE` environment variable is set to "DELTA" (case insensitive), the exporter will be
    * DELTA.
    *
    * @return
    */
  def metricExporter(endpoint: String, preference: AggregationTemporality): OtlpGrpcMetricExporter = {
    val builder = OtlpGrpcMetricExporter
      .builder()

    val preferenceSelector = preference match {
      case AggregationTemporality.DELTA => AggregationTemporalitySelector.deltaPreferred()
      case AggregationTemporality.CUMULATIVE => AggregationTemporalitySelector.alwaysCumulative()
    }

    builder.setAggregationTemporalitySelector(preferenceSelector)

    customMetricExporter(
      builder
        .setEndpoint(endpoint)
    ).build()
  }

  /** The OTLP endpoint to use for metrics.
    *
    * Uses the [[OtlpEnv#otelMetricsEndpoint]] method to determine the endpoint, can be overridden to provide a different endpoint logic.
    *
    * Returns `None` if no endpoint is configured, in which case the OpenTelemetry metrics layer will not be configured.
    *
    * @return
    */
  def meterEndpoint: ZIO[Any, Nothing, Option[String]] = OtlpEnv.otelMetricsEndpoint

  /** Provides a meter provider for OpenTelemetry, which logs in OTLP Json format as gRPC if either of the following environment variables
    * is set:
    *   - `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`
    *   - `OTEL_EXPORTER_OTLP_ENDPOINT`
    * If `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE` environment variable is set to "DELTA" (case insensitive), the exporter will be
    * configured to prefer delta temporality for metrics, otherwise it will use the default cumulative temporality.
    */
  override def meterProvider: URIO[Scope, Option[SdkMeterProvider]] =
    for {
      endpointOption <- meterEndpoint
      endpoint <- endpointOption match {
        case Some(endpoint) => provider(endpoint).map(Some(_))
        case None           => ZIO.none
      }
    } yield endpoint

  /** Allows to customize the periodic metric reader. */
  def customMetricReader(reader: PeriodicMetricReaderBuilder): PeriodicMetricReaderBuilder = reader

  private def metricReader(metricExporter: OtlpGrpcMetricExporter): MetricReader =
    customMetricReader(
      PeriodicMetricReader
        .builder(metricExporter)
        .setInterval(5.second)
    ).build()

  /** Allows to customize the meter provider. */
  def customizeSdkMeterProvider(builder: SdkMeterProviderBuilder): SdkMeterProviderBuilder = builder

  /** Creates a meter provider for OpenTelemetry
    */
  private def sdkMeterProvider(metricReader: MetricReader): SdkMeterProvider =
    customizeSdkMeterProvider(
      SdkMeterProvider
        .builder()
        .registerMetricReader(metricReader)
        .setResource(
          Resource.create(
            attributes
          )
        )
    )
      .build()

  private def provider(endpoint: String): URIO[Scope, SdkMeterProvider] =

    for {
      _ <- ZIO.logInfo(s"Configuring OpenTelemetry metrics to $endpoint")
      preference <- OtlpEnv.metricsTemporalityPreference
      metricExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(metricExporter(endpoint, preference))
      )
      metricReader <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            metricReader(metricExporter)
          )
        )
      meterProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            sdkMeterProvider(metricReader)
          )
        )
    } yield meterProvider

  override def zioMetrics: URLayer[io.opentelemetry.api.OpenTelemetry & ContextStorage, Unit] =
    if (collectZioMetrics) OpenTelemetry.metrics("zio") >>> OpenTelemetry.zioMetrics
    else ZLayer.unit

  /** A OpenTelemetry metrics layer, with configurable instrumentation scope name, version and schema url.
    *
    * @param instrumentationScopeName
    * @param instrumentationVersion
    * @param schemaUrl
    * @param logAnnotated
    * @return
    */
  override def otel4zMetrics(
      instrumentationScopeName: String
  ): URLayer[io.opentelemetry.api.OpenTelemetry & ContextStorage, Meter] = OpenTelemetry.metrics(
    instrumentationScopeName = instrumentationScopeName
  )

  /** A OpenTelemetry metrics interceptor for tapir, with configurable instrumentation scope name.
    *
    * It uses the OpenTelemetry instance from the environment, which is provided by the [[ZIOpenTelemetry]] trait bootstrap layer.
    *
    * @param instrumentationScopeName
    * @param otel
    * @return
    */
  def otel4zMetricsInterceptor(
      instrumentationScopeName: String = "tapir"
  )(implicit otel: api.OpenTelemetry): MetricsRequestInterceptor[Task] = {
    val meter: api.metrics.Meter = otel.meterBuilder(instrumentationScopeName).build()

    val metrics = OpenTelemetryMetrics.default[Task](meter)

    metrics.metricsInterceptor()
  }

}
