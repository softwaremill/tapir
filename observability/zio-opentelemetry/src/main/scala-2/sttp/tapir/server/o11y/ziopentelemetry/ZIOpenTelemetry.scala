package sttp.tapir.server.o11y.ziopentelemetry

import zio.ZIOApp

/** ZIOpenTelemetry is a trait that provides a ZIO layer for OpenTelemetry.
  * @param name
  */
trait ZIOpenTelemetry extends ZIOpenTelemetryBase {
  this: ZIOApp =>

  def version: Option[String] = None

  def environment: Option[String] = None
}
