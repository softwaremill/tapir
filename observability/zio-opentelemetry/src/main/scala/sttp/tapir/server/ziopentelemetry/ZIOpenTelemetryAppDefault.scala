package sttp.tapir.server.ziopentelemetry

import io.opentelemetry.api
import zio._
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.metrics.Meter
import zio.telemetry.opentelemetry.tracing.Tracing

/** ZIOtelAppDefault provides a default implementation of the ZIOtel trait.
  *
  * With logging, metrics and tracing enabled.
  */
abstract class ZIOpenTelemetryAppDefault(
    val resourceName: String,
    val version: Option[String] = None,
    val environment: Option[String] = None
) extends ZIOpenTelemetryApp
    with Logging
    with Metrics
    with Traces {

  /** The environment for the [[ZIOApp]]
    *
    * This is the environment that will be used to run the ZIO application, hence provided by bootstrap.
    *
    * It includes:
    *   - the OpenTelemetry instance.
    *   - the ContextStorage instance.
    *   - the Meter instance.
    *   - the Tracing instance.
    */
  override type Environment = api.OpenTelemetry with ContextStorage with Meter with Tracing

  /** The tag for the ZIOpenTelemetry trait. */
  def environmentTag: Tag[Environment] =
    Tag[Environment]

  /** The bootstrap layer for the ZIOpenTelemetry trait.
    *
    * This is the layer that will be used to bootstrap the ZIO application. It includes the OpenTelemetry layer, the Tracing layer, and the
    * Meter layer.
    */
  override def bootstrap: ZLayer[ZIOAppArgs, Any, Environment] =
    observabilityLayer
}
