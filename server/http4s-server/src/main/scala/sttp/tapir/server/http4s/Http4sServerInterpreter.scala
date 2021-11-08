package sttp.tapir.server.http4s

import cats.arrow.FunctionK
import cats.effect.{Async, Sync}
import cats.~>
import org.http4s._
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint

trait Http4sServerInterpreter[F[_]] extends Http4sServerToHttpInterpreter[F, F] {
  def toRoutes(se: ServerEndpoint[Any, F]): HttpRoutes[F] =
    toRoutes(List(se))

  def toRoutes(serverEndpoints: List[ServerEndpoint[Any, F]]): HttpRoutes[F] =
    toHttp(serverEndpoints, webSocketBuilder = None)(fToG)(gToF)

  def toWebSocketRoutes(se: ServerEndpoint[Fs2Streams[F] with WebSockets, F]): WebSocketBuilder2[F] => HttpRoutes[F] =
    toWebSocketRoutes(List(se))

  def toWebSocketRoutes(
      serverEndpoints: List[ServerEndpoint[Fs2Streams[F] with WebSockets, F]]
  ): WebSocketBuilder2[F] => HttpRoutes[F] =
    wsb => toHttp(serverEndpoints, webSocketBuilder = Some(wsb))(fToG)(gToF)
}

object Http4sServerInterpreter {
  def apply[F[_]]()(implicit _fa: Async[F]): Http4sServerInterpreter[F] = {
    new Http4sServerInterpreter[F] {
      override implicit def gs: Sync[F] = _fa
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> F = FunctionK.id[F]
      override def gToF: F ~> F = FunctionK.id[F]
    }
  }

  def apply[F[_]](
      serverOptions: Http4sServerOptions[F, F]
  )(implicit _fa: Async[F]): Http4sServerInterpreter[F] = {
    new Http4sServerInterpreter[F] {
      override implicit def gs: Sync[F] = _fa
      override implicit def fa: Async[F] = _fa
      override def fToG: F ~> F = FunctionK.id[F]
      override def gToF: F ~> F = FunctionK.id[F]
      override def http4sServerOptions: Http4sServerOptions[F, F] = serverOptions
    }
  }
}
