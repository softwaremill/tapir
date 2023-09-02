package sttp.tapir.server
import zio.Task
import zio.http.WebSocketChannel

package object ziohttp {
  type WebSocketHandler = WebSocketChannel => Task[Unit]

  type ZioResponseBody =
    Either[WebSocketHandler, ZioHttpResponseBody]

}
