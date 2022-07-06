package sttp.tapir.server.http4s.ztapir

import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.WebSockets
import sttp.tapir.integ.cats.ServerRoutesInstances
import sttp.tapir.server.ServerRoutes
import sttp.tapir.ztapir.ZServerEndpoint

object ZHttp4sServerRoutes extends ServerRoutesInstances {

  implicit class ZHttp4sServerEndpointOps[R](se: ZServerEndpoint[R, ZioStreams]) {
    def toRoutes(interpreter: ZHttp4sServerInterpreter[R]): ZHttp4sServerRoutes[R] =
      ServerRoutes.one(se)(interpreter.from(_).toRoutes)
  }

  implicit class ZHttp4sServerEndpointListOps[R](se: List[ZServerEndpoint[R, ZioStreams]]) {
    def toRoutes(interpreter: ZHttp4sServerInterpreter[R]): ZHttp4sServerRoutes[R] =
      ServerRoutes.fromList(se)(interpreter.from(_).toRoutes)
  }

  // websockets
  implicit class ZHttp4sServerEndpointWebsocketOps[R](se: ZServerEndpoint[R, ZioStreams with WebSockets]) {
    def toWebSocketRoutes(interpreter: ZHttp4sServerInterpreter[R]): ZHttp4sServerWebSocketRoutes[R] =
      ServerRoutes.one(se)(interpreter.fromWebSocket(_).toRoutes)
  }

  implicit class ZHttp4sServerEndpointWebsocketListOps[R](se: List[ZServerEndpoint[R, ZioStreams with WebSockets]]) {
    def toWebSocketRoutes(interpreter: ZHttp4sServerInterpreter[R]): ZHttp4sServerWebSocketRoutes[R] =
      ServerRoutes.fromList(se)(interpreter.fromWebSocket(_).toRoutes)
  }
}
