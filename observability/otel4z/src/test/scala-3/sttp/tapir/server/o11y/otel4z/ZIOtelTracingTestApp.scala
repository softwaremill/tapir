package sttp.tapir.server.o11y.otel4z

import zio.*

object TestZIOApp extends ZIOApp with ZIOpenTelemetry("test-service") {

  override def run = ZIO.unit
}
