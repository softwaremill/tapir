package sttp.tapir.server.o11y.otel4z

import zio._
import io.opentelemetry.api
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.trace.SdkTracerProvider
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage

/** OtelProviders is a case class that holds the OpenTelemetry providers for tracing, metrics and logging.
  *
  * It is used to build the OpenTelemetry
  *
  * @param tracerProvider
  * @param meterProvider
  * @param loggerProvider
  */
case class OtelProviders(
    tracerProvider: Option[SdkTracerProvider],
    meterProvider: Option[SdkMeterProvider],
    loggerProvider: Option[SdkLoggerProvider]
) {

  def build(): OpenTelemetrySdk = {
    val builder = OpenTelemetrySdk
      .builder()
    tracerProvider.foreach(builder.setTracerProvider)
    meterProvider.foreach(builder.setMeterProvider)
    loggerProvider.foreach(builder.setLoggerProvider)
    builder.build()
  }

}

object ZIOpenTelemetryLayer {

  /** The OpenTelemetry layer for the ZIOpenTelemetry trait.
    *
    * This is a separate method pulled by the bootstrap layer, as it is used to provide the OpenTelemetry layer to the server options, which
    * are provided by the ZIO application itself. This allows the OpenTelemetry layer to be used
    *
    * @param resourceName
    * @return
    */
  def live(withZioMetrics: Boolean): RLayer[OtelProviders with ContextStorage, api.OpenTelemetry] =
    if (withZioMetrics)
      otel >+> (OpenTelemetry.metrics("zio") >>> OpenTelemetry.zioMetrics)
    else otel

  private def otel = ZLayer.scoped[OtelProviders](
    for {
      otelProviders <- ZIO.service[OtelProviders]
      openTelemetry <- ZIO.fromAutoCloseable(
        ZIO.succeed(otelProviders.build())
      )

    } yield openTelemetry
  )

}
