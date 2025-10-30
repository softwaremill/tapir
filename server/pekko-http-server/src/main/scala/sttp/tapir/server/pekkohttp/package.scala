package sttp.tapir.server

import org.apache.pekko.http.scaladsl.model.ResponseEntity
import org.apache.pekko.http.scaladsl.model.ws.Message
import org.apache.pekko.stream.scaladsl.{Flow, Source}
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{CodecFormat, StreamBodyIO, streamTextBody}

import java.nio.charset.StandardCharsets

package object pekkohttp {
  type PekkoResponseBody = Either[Flow[Message, Message, Any], ResponseEntity]

  val serverSentEventsBody: StreamBodyIO[Source[ByteString, Any], Source[ServerSentEvent, Any], PekkoStreams] =
    streamTextBody(PekkoStreams)(CodecFormat.TextEventStream(), Some(StandardCharsets.UTF_8))
      .map(PekkoServerSentEvents.parseBytesToSSE)(PekkoServerSentEvents.serialiseSSEToBytes)
}
