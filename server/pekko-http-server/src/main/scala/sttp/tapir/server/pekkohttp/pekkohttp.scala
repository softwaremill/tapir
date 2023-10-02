package sttp.tapir.server

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{CodecFormat, Schema, StreamBodyIO, streamBody}

import java.nio.charset.Charset

package object pekkohttp extends TapirPekkoHttpServer {
  val serverSentEventsBody: StreamBodyIO[Source[ByteString, Any], Source[ServerSentEvent, Any], PekkoStreams] =
    streamBody(PekkoStreams)(Schema.binary, CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(PekkoServerSentEvents.parseBytesToSSE)(PekkoServerSentEvents.serialiseSSEToBytes)
}
