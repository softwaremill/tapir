package sttp.tapir.server.o11y.otel4z

import zio.ZIOApp

/** ZIOpenTelemetry is a trait that provides a ZIO layer for OpenTelemetry.
  * @param name
  */
trait ZIOpenTelemetry extends ZIOpentelemetryBase {
  this: ZIOApp =>

  def version: Option[String] = None
  
  def environment: Option[String] = None  
}


