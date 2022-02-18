package sttp.tapir.server.armeria.zio

import _root_.zio.{Runtime, Task}
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria.{ArmeriaTestServerInterpreter, TapirService}
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

class ArmeriaZioTestServerInterpreter extends ArmeriaTestServerInterpreter[ZioStreams, Task] {
  import ArmeriaZioTestServerInterpreter._

  override def route(
      e: ServerEndpoint[ZioStreams, Task],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[Task]] = None
  ): TapirService[ZioStreams, Task] = {
    val options: ArmeriaZioServerOptions[Task] = {
      ArmeriaZioServerOptions.customInterceptors
        .metricsInterceptor(metricsInterceptor)
        .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
        .options
    }
    ArmeriaZioServerInterpreter(options).toService(e)
  }

  override def route(es: List[ServerEndpoint[ZioStreams, Task]]): TapirService[ZioStreams, Task] = {
    ArmeriaZioServerInterpreter[Any]().toService(es)
  }
}

object ArmeriaZioTestServerInterpreter {
  implicit val runtime: Runtime[zio.ZEnv] = Runtime.default
}
