package sttp.tapir.server.http4s.ztapir

import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.ztapir._
import zio._
import zio.interop.catz._

trait ZHttp4sServerInterpreter[R] {

  def zHttp4sServerOptions[R2 <: R]: Http4sServerOptions[RIO[R2, *]] =
    Http4sServerOptions.default[RIO[R2, *]]

  def from(se: ZServerEndpoint[R, ZioStreams]): ServerEndpointsToRoutes = from(List(se))

  def from(serverEndpoints: List[ZServerEndpoint[R, ZioStreams]]): ServerEndpointsToRoutes =
    new ServerEndpointsToRoutes(serverEndpoints)

  def fromWebSocket(se: ZServerEndpoint[R, ZioStreams with WebSockets]): WebSocketServerEndpointsToRoutes = fromWebSocket(List(se))

  def fromWebSocket(serverEndpoints: List[ZServerEndpoint[R, ZioStreams with WebSockets]]): WebSocketServerEndpointsToRoutes =
    new WebSocketServerEndpointsToRoutes(serverEndpoints)

  class ServerEndpointsToRoutes(
      serverEndpoints: List[ZServerEndpoint[R, ZioStreams]]
  ) {
    def toRoutes[R2]: HttpRoutes[RIO[R & R2, *]] = {
      Http4sServerInterpreter(zHttp4sServerOptions[R & R2]).toRoutes(
        serverEndpoints.map(se => ConvertStreams(se.widen[R & R2]))
      )
    }
  }

  class WebSocketServerEndpointsToRoutes(
      serverEndpoints: List[ZServerEndpoint[R, ZioStreams with WebSockets]]
  ) {
    def toRoutes[R2]: WebSocketBuilder2[RIO[R & R2, *]] => HttpRoutes[RIO[R & R2, *]] = {
      Http4sServerInterpreter(zHttp4sServerOptions[R & R2]).toWebSocketRoutes(
        serverEndpoints.map(se => ConvertStreams(se.widen[R & R2]))
      )
    }
  }
}

object ZHttp4sServerInterpreter {
  def apply[R](): ZHttp4sServerInterpreter[R] = {
    new ZHttp4sServerInterpreter[R] {}
  }

  def apply[R](
      serverOptions: Http4sServerOptions[RIO[R, *]]
  ): ZHttp4sServerInterpreter[R] = {
    new ZHttp4sServerInterpreter[R] {
      override def zHttp4sServerOptions[R2 <: R]: Http4sServerOptions[RIO[R2, *]] =
        serverOptions.asInstanceOf[Http4sServerOptions[RIO[R2, *]]]
    }
  }
}
