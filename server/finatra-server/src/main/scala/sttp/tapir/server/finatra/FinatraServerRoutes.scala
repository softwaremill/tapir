package sttp.tapir.server.finatra

import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

import com.twitter.util.Future

object FinatraServerRoutes {

  implicit class FinatraServerEndpointOps(se: ServerEndpoint[Any, Future]) {
    def toRoute(interpreter: FinatraServerInterpreter): FinatraServerRoutes =
      ServerRoutes.one(se)(interpreter.toRoute)
  }
}
