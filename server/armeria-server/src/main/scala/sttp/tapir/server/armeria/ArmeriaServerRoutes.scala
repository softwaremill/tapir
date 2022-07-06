package sttp.tapir.server.armeria

import sttp.capabilities.armeria.ArmeriaStreams
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

import scala.concurrent.Future

object ArmeriaServerRoutes {

  implicit class ArmeriaServerEndpointOps(se: ServerEndpoint[ArmeriaStreams, Future]) {
    def toService(interpreter: ArmeriaFutureServerInterpreter): ArmeriaServerRoutes =
      ServerRoutes.one(se)(interpreter.toService)
  }

  implicit class ArmeriaServerEndpointListOps(se: List[ServerEndpoint[ArmeriaStreams, Future]]) {
    def toService(interpreter: ArmeriaFutureServerInterpreter): ArmeriaServerRoutes =
      ServerRoutes.fromList(se)(interpreter.toService)
  }
}
