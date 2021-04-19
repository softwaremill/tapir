package sttp.tapir.server

import fs2.Pipe
import org.http4s.EntityBody
import org.http4s.websocket.WebSocketFrame
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{CodecFormat, StreamBodyIO, streamTextBody}

import java.nio.charset.Charset

package object http4s {
  private[http4s] type Http4sResponseBody[F[_]] = Either[F[Pipe[F, WebSocketFrame, WebSocketFrame]], EntityBody[F]]

  def serverSentEventsBody[F[_]]: StreamBodyIO[fs2.Stream[F, Byte], fs2.Stream[F, ServerSentEvent], Fs2Streams[F]] = {
    val fs2Streams = Fs2Streams[F]
    streamTextBody(fs2Streams)(CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(Http4sServerSentEvents.parseBytesToSSE(fs2Streams))(Http4sServerSentEvents.serialiseSSEToBytes(fs2Streams))
  }
}
