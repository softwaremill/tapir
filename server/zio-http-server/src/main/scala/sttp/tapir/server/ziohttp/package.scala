package sttp.tapir.server
import zio.http.WebSocketChannelEvent
import zio.{ZIO, stream}

package object ziohttp {
  type WebSocketHandler =
    stream.Stream[Throwable, WebSocketChannelEvent] => ZIO[Any, Throwable, stream.Stream[Throwable, WebSocketChannelEvent]]

  type ZioResponseBody =
    Either[WebSocketHandler, ZioHttpResponseBody]

}
