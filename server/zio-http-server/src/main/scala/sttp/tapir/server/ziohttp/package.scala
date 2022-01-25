package sttp.tapir.server

import zhttp.socket.{Socket, WebSocketFrame}
import zio.stream.Stream

package object ziohttp {
  private[ziohttp] type ZioResponseBody = Either[Socket[Any, Throwable, WebSocketFrame, WebSocketFrame], Stream[Throwable, Byte]]
}
