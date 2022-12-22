package sttp.tapir.server.armeria.cats

import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria.{ArmeriaTestServerInterpreter, TapirService}

class ArmeriaCatsTestServerInterpreter(dispatcher: Dispatcher[IO])
    extends ArmeriaTestServerInterpreter[Fs2Streams[IO], IO, ArmeriaCatsServerOptions[IO]] {

  override def route(es: List[ServerEndpoint[Fs2Streams[IO], IO]], interceptors: Interceptors): TapirService[Fs2Streams[IO], IO] = {
    val options: ArmeriaCatsServerOptions[IO] = interceptors(ArmeriaCatsServerOptions.customiseInterceptors[IO](dispatcher)).options
    ArmeriaCatsServerInterpreter(options).toService(es)
  }
}
