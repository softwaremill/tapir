package sttp.tapir.server.finatra.cats

import sttp.tapir.integ.cats.ServerRoutesInstances
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

object FinatraCatsServerRoutes extends ServerRoutesInstances {

  implicit class FinatraCatsServerEndpointOps[F[_]](se: ServerEndpoint[Any, F]) {
    def toRoute(interpreter: FinatraCatsServerInterpreter[F]): FinatraCatsServerRoutes[F] =
      ServerRoutes.one(se)(interpreter.toRoute)
  }
}
