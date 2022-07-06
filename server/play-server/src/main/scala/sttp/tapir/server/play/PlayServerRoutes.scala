package sttp.tapir.server.play

import play.api.routing.Router.Routes
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

import scala.concurrent.Future

object PlayServerRoutes {

  type PlayServerRoutes = ServerRoutes[Future, Routes]

  def concat(a: PlayServerRoutes, b: PlayServerRoutes): PlayServerRoutes =
    a.combine(b)((a, b) => a.orElse(b))

  // syntax
  implicit class PlayServerEndpointsOps(thisRoutes: PlayServerRoutes) {
    def orElse(thatRoutes: PlayServerRoutes): PlayServerRoutes =
      PlayServerRoutes.concat(thisRoutes, thatRoutes)
  }

  implicit class PlayServerEndpointOps(se: ServerEndpoint[AkkaStreams with WebSockets, Future]) {
    def toRoutes(interpreter: PlayServerInterpreter): PlayServerRoutes =
      ServerRoutes.one(se)(interpreter.toRoutes)
  }

  implicit class PlayServerEndpointListOps(se: List[ServerEndpoint[AkkaStreams with WebSockets, Future]]) {
    def toRoutes(interpreter: PlayServerInterpreter): PlayServerRoutes =
      ServerRoutes.fromList(se)(interpreter.toRoutes)
  }
}
