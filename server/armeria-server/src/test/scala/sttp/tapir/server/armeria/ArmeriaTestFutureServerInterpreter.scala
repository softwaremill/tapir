package sttp.tapir.server.armeria

import scala.concurrent.Future
import sttp.capabilities.armeria.ArmeriaStreams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

class ArmeriaTestFutureServerInterpreter extends ArmeriaTestServerInterpreter[ArmeriaStreams, Future] {

  override def route(
      e: ServerEndpoint[ArmeriaStreams, Future],
      decodeFailureHandler: Option[DecodeFailureHandler] = None,
      metricsInterceptor: Option[MetricsRequestInterceptor[Future]] = None
  ): TapirService[ArmeriaStreams, Future] = {
    val serverOptions: ArmeriaFutureServerOptions = ArmeriaFutureServerOptions.customInterceptors
      .metricsInterceptor(metricsInterceptor)
      .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
      .options
    ArmeriaFutureServerInterpreter(serverOptions).toService(e)
  }

  override def route(es: List[ServerEndpoint[ArmeriaStreams, Future]]): TapirService[ArmeriaStreams, Future] =
    ArmeriaFutureServerInterpreter().toService(es)
}
