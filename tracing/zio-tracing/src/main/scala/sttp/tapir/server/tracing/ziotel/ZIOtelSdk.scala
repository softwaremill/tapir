package sttp.tapir.server.tracing.ziotel

import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.api

import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry

object ZIOtelSdk {

  def custom(resourceName: String): TaskLayer[api.OpenTelemetry] =
    OpenTelemetry.custom(
      for {

        tracerProvider <- TracerProvider.grpc(resourceName)
        meterProvider <- MeterProvider.grpc(resourceName)
        loggerProvider <- LoggerProvider.grpc(resourceName)
        openTelemetry <- ZIO.fromAutoCloseable(
          ZIO.succeed(
            OpenTelemetrySdk
              .builder()
              .setTracerProvider(tracerProvider)
              .setMeterProvider(meterProvider)
              .setLoggerProvider(loggerProvider)
              .build
          )
        )
        _ <- ZIO.fromAutoCloseable(
          ZIO.succeed(RuntimeTelemetry.create(openTelemetry))
        )
      } yield openTelemetry
    )

}
