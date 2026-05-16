package sttp.tapir.server.o11y.otel4z

import zio.ZIOApp

/** ZIOpenTelemetry is a trait that provides a ZIO layer for OpenTelemetry.
  * @param name
  */
trait ZIOpenTelemetry(val resourceName: String) extends ZIOpentelemetryBase {
  this: ZIOApp =>
}

trait ZIOpenTelemetryFull(val resourceName: String) extends ZIOpentelemetryBase with Metrics with Traces {
  this: ZIOApp =>
}


