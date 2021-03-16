package sttp.tapir.server

import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{CodecFormat, Schema, StreamBodyIO, streamBody}

import java.nio.charset.Charset

package object http4s extends TapirHttp4sServer {
  def serverSentEventsBody[F[_]]: StreamBodyIO[fs2.Stream[F, Byte], fs2.Stream[F, ServerSentEvent], Fs2Streams[F]] = {
    val fs2Streams = Fs2Streams[F]
    streamBody(fs2Streams)(Schema.binary, CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(Http4sServerSentEvents.parseBytesToSSE(fs2Streams))(Http4sServerSentEvents.serialiseSSEToBytes(fs2Streams))
  }
}
