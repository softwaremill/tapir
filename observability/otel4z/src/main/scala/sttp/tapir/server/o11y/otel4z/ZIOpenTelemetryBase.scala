package sttp.tapir.server.o11y.otel4z

import zio._
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry

trait ZIOpenTelemetryBase {


  def runtimeTelemetry =  ZLayer.fromZIO(
          for {
            openTelemetry <- ZIO.service[io.opentelemetry.api.OpenTelemetry]
            _ <- ZIO.fromAutoCloseable(ZIO.succeed(RuntimeTelemetry.create(openTelemetry)))
          } yield ()
        )
  
  


}
