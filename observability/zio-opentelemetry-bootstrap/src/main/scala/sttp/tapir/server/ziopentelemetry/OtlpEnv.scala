package sttp.tapir.server.ziopentelemetry
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import zio._


/**
  * Provides a way to configure the OTLP exporter via environment variables.
  * 
  * - `OTEL_EXPORTER_OTLP_ENDPOINT` - The OTLP endpoint to use for all telemetry data.
  * - `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` - The OTLP endpoint to use for logs.
  * - `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` - The OTLP endpoint to use for traces.
  * - `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` - The OTLP endpoint to use for metrics.
  * - `OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE` - The temporality preference for metrics.
  */
object OtlpEnv {

  sealed trait Module {
    val name: String
    val envVar: String
  }

  case object Logging extends Module{
    val name: String = "Logging"
    val envVar: String = "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT"
  }

  case object Traces extends Module{
    val name: String = "Traces"
    val envVar: String = "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"
  }

  case object Metrics extends Module{
    val name: String = "Metrics"
    val envVar: String = "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT"
  }

  private val OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT"
  private val OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE = "OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE"


  /** OTLP gRPC endpoint to export telemetry data to.
    *
    * It can be set via:
    *
    *   - environment variable provided as `envVar`
    *   - environment variable "OTEL_EXPORTER_OTLP_ENDPOINT"
    *   - defaults to "http://localhost:4317"
    *
    * See https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/#otel_exporter_otlp_endpoint.
    *
    * @param envVar
    * @return
    */
  def endpoint(envVar: String): Option[String] =
    sys.env
      .get(envVar)
      .orElse(sys.env.get(OTEL_EXPORTER_OTLP_ENDPOINT))


  /** Uses the `OTEL_LOG_LEVEL` environment variable to determine the log level.
    *
    * By default, this is set to `INFO`. You can override this to change the log level, e.g. to `DEBUG` for more verbose logging.
    */
  def logLevel = sys.env.getOrElse("OTEL_LOG_LEVEL", "INFO").toUpperCase match {
    case "DEBUG" => LogLevel.Debug
    case "INFO"  => LogLevel.Info
    case "WARN"  => LogLevel.Warning
    case "ERROR" => LogLevel.Error
    case "TRACE" => LogLevel.Trace
    case "ALL"   => LogLevel.All
    case _       => LogLevel.Info
  }

  def otelLogEndpoint: ZIO[Any, Nothing, Option[String]] = OtlpEnv.otelEndpoint(Logging)

  def otelTracesEndpoint: ZIO[Any, Nothing, Option[String]] = OtlpEnv.otelEndpoint(Traces)
  
  def otelMetricsEndpoint: ZIO[Any, Nothing, Option[String]] = OtlpEnv.otelEndpoint(Metrics)


  def metricsTemporalityPreference: UIO[AggregationTemporality] = 
    for
      _ <- ZIO.logTrace(s"Configuring OpenTelemetry metrics temporality preference")
      maybe = sys.env
           .get(OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE)
           .map(_.toUpperCase)
      _ <- ZIO.logDebug(s"$OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE: $maybe")
      preference <- maybe match {
        case Some("DELTA") => ZIO.succeed(AggregationTemporality.DELTA)
        case Some("CUMULATIVE") => ZIO.succeed(AggregationTemporality.CUMULATIVE)
        case Some(other) =>
           ZIO.logWarning(s"Unknown OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE: $other, defaulting to CUMULATIVE") *>
           ZIO.succeed(AggregationTemporality.CUMULATIVE)
        case None =>
           ZIO.succeed(AggregationTemporality.CUMULATIVE)
      }
      _ <- ZIO.logInfo(s"OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE: $preference")
    yield preference

  private def otelEndpoint(module: Module):  ZIO[Any, Nothing, Option[String]] = OtlpEnv.endpoint(module.envVar) match {
    case None =>
      ZIO.logInfo(
        s"No OTLP $module endpoint configured, skipping this OpenTelemetry setup. To enable it, set either ${module.envVar} or $OTEL_EXPORTER_OTLP_ENDPOINT environment variable."
      ) *> ZIO.succeed(None)

    case Some(endpoint) =>
      ZIO.some(endpoint)
  }
}
