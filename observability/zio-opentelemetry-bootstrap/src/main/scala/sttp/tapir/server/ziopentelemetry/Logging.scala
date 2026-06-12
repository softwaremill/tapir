package sttp.tapir.server.ziopentelemetry

import zio._
import io.opentelemetry.api
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.sdk.logs.`export`.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource

/** A trait that provides a logger provider for OpenTelemetry, which logs in OTLP gRPC format with [[LoggerProvider]]
  */
trait Logging {
  this: ZIOpenTelemetryApp =>

  /** The log level to use for the OpenTelemetry logger provider.
    *
    * Uses the [[oltpLogLevel]] method to determine the log level, can be overridden to provide a different log level logic.
    *
    * By default, this is set to `INFO`. You can override this to change the log level, e.g. to `DEBUG` for more verbose logging.
    */
  def logLevel = oltpLogLevel

  /** Uses the `OTEL_LOG_LEVEL` environment variable to determine the log level.
    *
    * By default, this is set to `INFO`. You can override this to change the log level, e.g. to `DEBUG` for more verbose logging.
    */
  final protected def oltpLogLevel = sys.env.getOrElse("OTEL_LOG_LEVEL", "INFO").toUpperCase match {
    case "DEBUG" => LogLevel.Debug
    case "INFO"  => LogLevel.Info
    case "WARN"  => LogLevel.Warning
    case "ERROR" => LogLevel.Error
    case "TRACE" => LogLevel.Trace
    case "ALL"   => LogLevel.All
    case _       => LogLevel.Info
  }

  /** The OTLP endpoint to use for logging.
    *
    * Default uses the `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` environment variable to determine the endpoint. If not set, it will use the
    * `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable. If neither is set, it will return `None`.
    *
    * @return
    */
  def logEndpoint = otelLogEndpoint

  /** The OTLP endpoint to use for logging.
    *
    * Uses the `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` environment variable to determine the endpoint. If not set, it will use the
    * `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable. If neither is set, it will return `None`.
    *
    * @return
    */
  final protected def otelLogEndpoint: ZIO[Any, Nothing, Option[String]] = OtlpEndpoint("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT") match {
    case None =>
      ZIO.logInfo(
        "No OTLP logs endpoint configured, skipping OpenTelemetry logging setup. To enable it, set either OTEL_EXPORTER_OTLP_LOGS_ENDPOINT or OTEL_EXPORTER_OTLP_ENDPOINT environment variable."
      ) *> ZIO.succeed(None)

    case Some(endpoint) =>
      ZIO.some(endpoint)
  }

  /** Provides a logger provider for OpenTelemetry, which logs in OTLP gRPC format with [[LoggerProvider]]
    */
  override def logProvider: URIO[Scope, Option[SdkLoggerProvider]] = for {

    endpointOption <- logEndpoint

    endpoint <- endpointOption match {
      case Some(endpoint) => provider(endpoint).map(Some(_))
      case None           => ZIO.none
    }

  } yield endpoint

  private def provider(endpoint: String): URIO[Scope, SdkLoggerProvider] =
    for {
      _ <- ZIO.logInfo(s"Configuring OpenTelemetry logging to $endpoint")
      logRecordExporter <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            OtlpGrpcLogRecordExporter
              .builder()
              .setEndpoint(endpoint)
              .build()
          )
        )
      logRecordProcessor <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(SimpleLogRecordProcessor.create(logRecordExporter))
        )
      loggerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkLoggerProvider
              .builder()
              .setResource(
                Resource.create(
                  attributes
                )
              )
              .addLogRecordProcessor(logRecordProcessor)
              .build()
          )
        )
    } yield loggerProvider

  /** A OpenTelemetry logging layer.
    *
    * @return
    */
  override def otel4zLogging: URLayer[api.OpenTelemetry with ContextStorage, Unit] = OpenTelemetry.logging(
    instrumentationScopeName = resourceName,
    logLevel = logLevel
  )
}
