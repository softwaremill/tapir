package sttp.tapir.server.o11y.otel4z

import zio.ZIOApp

/** ZIOpenTelemetry is a trait that provides a ZIO layer for OpenTelemetry.
  * @param name
  */
trait ZIOpenTelemetry extends ZIOtelBase {
  this: ZIOApp =>
}

trait ZIOpenTelemetryFull extends ZIOtelBase with Metrics with Traces {
  this: ZIOApp =>
}

object ZIOpenTelemetry extends ZIOpenTelemetryBase