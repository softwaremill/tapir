package sttp.tapir.server

import fs2.Pipe
import org.http4s.EntityBody
import org.http4s.websocket.WebSocketFrame

package object http4s {
  private[http4s] type Http4sResponseBody[F[_]] = Either[F[Pipe[F, WebSocketFrame, WebSocketFrame]], EntityBody[F]]
}
