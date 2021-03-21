package sttp.tapir.server.armeria.cats

import cats.effect.Async
import cats.effect.std.Dispatcher
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.armeria.TapirService

trait ArmeriaCatsServerInterpreter[F[_]] {

  implicit def fa: Async[F]

  def armeriaServerOptions: ArmeriaCatsServerOptions[F]

  def toRoute(serverEndpoint: ServerEndpoint[Fs2Streams[F], F]): TapirService[Fs2Streams[F], F] =
    toRoute(List(serverEndpoint))

  def toRoute(serverEndpoints: List[ServerEndpoint[Fs2Streams[F], F]]): TapirService[Fs2Streams[F], F] =
    TapirCatsService(serverEndpoints, armeriaServerOptions)
}

object ArmeriaCatsServerInterpreter {
  def apply[F[_]](dispatcher: Dispatcher[F])(implicit _fa: Async[F]): ArmeriaCatsServerInterpreter[F] = {
    new ArmeriaCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def armeriaServerOptions: ArmeriaCatsServerOptions[F] = ArmeriaCatsServerOptions.default[F](dispatcher)(fa)
    }
  }

  def apply[F[_]](serverOptions: ArmeriaCatsServerOptions[F])(implicit _fa: Async[F]): ArmeriaCatsServerInterpreter[F] = {
    new ArmeriaCatsServerInterpreter[F] {
      override implicit def fa: Async[F] = _fa
      override def armeriaServerOptions: ArmeriaCatsServerOptions[F] = serverOptions
    }
  }
}
