package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server.Directives
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

import scala.concurrent.Future

object AkkaServerRoutes {

  def concat(a: AkkaServerRoutes, b: AkkaServerRoutes): AkkaServerRoutes =
    a.combine(b)((a, b) => Directives.concat(a, b))

  implicit class AkkaServerEndpointsOps(thisRoutes: AkkaServerRoutes) {
    def ~(thatRoutes: AkkaServerRoutes): AkkaServerRoutes =
      AkkaServerRoutes.concat(thisRoutes, thatRoutes)
  }

  implicit class AkkaServerEndpointOps(se: ServerEndpoint[AkkaStreams with WebSockets, Future]) {
    def toRoute(interpreter: AkkaHttpServerInterpreter): AkkaServerRoutes =
      ServerRoutes.one(se)(interpreter.toRoute)
  }

  implicit class AkkaServerEndpointListOps(se: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]) {
    def toRoute(interpreter: AkkaHttpServerInterpreter): AkkaServerRoutes =
      ServerRoutes.fromList(se)(interpreter.toRoute)
  }
}
