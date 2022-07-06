package sttp.tapir.server.armeria.cats

import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.WebSockets
import sttp.tapir.integ.cats.ServerRoutesInstances
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

object ArmeriaCatsServerRoutes extends ServerRoutesInstances {

  implicit class ArmeriaCatsServerEndpointOps[F[_]](se: ServerEndpoint[Fs2Streams[F], F]) {
    def toService(interpreter: ArmeriaCatsServerInterpreter[F]): ArmeriaCatsServerRoutes[F] =
      ServerRoutes.one(se)(interpreter.toService)
  }

  implicit class ArmeriaCatsServerEndpointListOps[F[_]](se: List[ServerEndpoint[Fs2Streams[F], F]]) {
    def toService(interpreter: ArmeriaCatsServerInterpreter[F]): ArmeriaCatsServerRoutes[F] =
      ServerRoutes.fromList(se)(interpreter.toService)
  }
}
