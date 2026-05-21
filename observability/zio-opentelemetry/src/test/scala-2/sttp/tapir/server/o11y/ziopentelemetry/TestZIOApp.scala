package sttp.tapir.server.o11y.ziopentelemetry

import zio._

object TestZIOApp extends ZIOApp with ZIOpenTelemetry {
  override def resourceName: String = "test-service"
  override def run = ZIO.unit
}
