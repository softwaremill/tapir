package sttp.tapir.server
import zio.Task
import zio.http.{WebSocketFrame => ZWebSocketFrame}

package object ziohttp {
  type F2F = ZWebSocketFrame => Task[List[ZWebSocketFrame]]

  type ZioResponseBody =
    Either[F2F, ZioHttpResponseBody]

}
