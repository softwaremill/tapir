package sttp.tapir.server

import akka.http.scaladsl.model.ResponseEntity
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{CodecFormat, StreamBodyIO, streamTextBody}

import java.nio.charset.Charset

package object akkahttp {
  private[akkahttp] type AkkaResponseBody = Either[Flow[Message, Message, Any], ResponseEntity]

  implicit val responseListener: AkkaBodyListener = new AkkaBodyListener

  val serverSentEventsBody: StreamBodyIO[Source[ByteString, Any], Source[ServerSentEvent, Any], AkkaStreams] =
    streamTextBody(AkkaStreams)(CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(AkkaServerSentEvents.parseBytesToSSE)(AkkaServerSentEvents.serialiseSSEToBytes)
}
