package sttp.tapir.server.armeria.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria.{ArmeriaTestServerInterpreter, TapirService}
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

class ArmeriaCatsTestServerInterpreter(dispatcher: Dispatcher[IO]) extends ArmeriaTestServerInterpreter[Fs2Streams[IO], IO] {

  override def route(
      e: ServerEndpoint[Fs2Streams[IO], IO],
      decodeFailureHandler: Option[DecodeFailureHandler],
      metricsInterceptor: Option[MetricsRequestInterceptor[IO]] = None
  ): TapirService[Fs2Streams[IO], IO] = {
    val options: ArmeriaCatsServerOptions[IO] = {
      ArmeriaCatsServerOptions
        .customInterceptors[IO](dispatcher)
        .metricsInterceptor(metricsInterceptor)
        .decodeFailureHandler(decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.default))
        .options
    }
    ArmeriaCatsServerInterpreter(options).toService(e)
  }

  override def route(es: List[ServerEndpoint[Fs2Streams[IO], IO]]): TapirService[Fs2Streams[IO], IO] = {
    ArmeriaCatsServerInterpreter(dispatcher).toService(es)
  }
}
