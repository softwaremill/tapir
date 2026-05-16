package sttp.tapir.server.o11y.otel4z

import zio.ZIOApp

/** ZIOpenTelemetry is a trait that provides a ZIO layer for OpenTelemetry.
  * @param name
  */
trait ZIOpenTelemetry(val resourceName: String, val version: Option[String]=None, val environment: Option[String]=None) extends ZIOpentelemetryBase {
  this: ZIOApp =>
}



