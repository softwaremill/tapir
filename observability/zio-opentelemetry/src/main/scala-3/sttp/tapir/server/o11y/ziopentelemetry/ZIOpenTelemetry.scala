package sttp.tapir.server.o11y.ziopentelemetry

import zio.ZIOApp
import sttp.tapir.server.o11y.ziopentelemetry.ZIOpenTelemetryBase

/** ZIOpenTelemetry is a trait that provides a ZIO layer for OpenTelemetry.
  * @param name
  */
trait ZIOpenTelemetry(val resourceName: String, val version: Option[String] = None, val environment: Option[String] = None)
    extends ZIOpenTelemetryBase {
  this: ZIOApp =>
}
