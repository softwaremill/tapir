package sttp.tapir.server

import fs2.Pipe
import org.http4s.{EntityBody, HttpRoutes}
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{streamTextBody, CodecFormat, StreamBodyIO}

import java.nio.charset.Charset

package object http4s {

  type Http4sServerRoutes[F[_]] = ServerRoutes[F, HttpRoutes[F]]
  type Http4sServerWebSocketRoutes[F[_]] = ServerRoutes[F, WebSocketBuilder2[F] => HttpRoutes[F]]

  // either a web socket, or a stream with optional length (if known)
  private[http4s] type Http4sResponseBody[F[_]] = Either[F[Pipe[F, WebSocketFrame, WebSocketFrame]], (EntityBody[F], Option[Long])]

  def serverSentEventsBody[F[_]]: StreamBodyIO[fs2.Stream[F, Byte], fs2.Stream[F, ServerSentEvent], Fs2Streams[F]] = {
    val fs2Streams = Fs2Streams[F]
    streamTextBody(fs2Streams)(CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(Http4sServerSentEvents.parseBytesToSSE[F])(Http4sServerSentEvents.serialiseSSEToBytes[F])
  }
}
