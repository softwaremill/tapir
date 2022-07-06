package sttp.tapir.server.http4s

import sttp.capabilities.fs2.Fs2Streams
import sttp.capabilities.WebSockets
import sttp.tapir.integ.cats.ServerRoutesInstances
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

object Http4sServerRoutes extends ServerRoutesInstances {

  implicit class Http4sServerEndpointOps[F[_]](se: ServerEndpoint[Fs2Streams[F], F]) {
    def toRoutes(interpreter: Http4sServerInterpreter[F]): Http4sServerRoutes[F] =
      ServerRoutes.one(se)(interpreter.toRoutes)
  }

  implicit class Http4sServerEndpointListOps[F[_]](se: List[ServerEndpoint[Fs2Streams[F], F]]) {
    def toRoutes(interpreter: Http4sServerInterpreter[F]): Http4sServerRoutes[F] =
      ServerRoutes.fromList(se)(interpreter.toRoutes)
  }

  // websockets
  implicit class Http4sServerEndpointWebsocketOps[F[_]](se: ServerEndpoint[Fs2Streams[F] with WebSockets, F]) {
    def toWebSocketRoutes(interpreter: Http4sServerInterpreter[F]): Http4sServerWebSocketRoutes[F] =
      ServerRoutes.one(se)(interpreter.toWebSocketRoutes)
  }

  implicit class Http4sServerEndpointWebsocketListOps[F[_]](se: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]]) {
    def toWebSocketRoutes(interpreter: Http4sServerInterpreter[F]): Http4sServerWebSocketRoutes[F] =
      ServerRoutes.fromList(se)(interpreter.toWebSocketRoutes)
  }
}
