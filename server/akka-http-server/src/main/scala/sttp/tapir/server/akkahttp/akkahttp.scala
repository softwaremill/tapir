package sttp.tapir.server

import akka.stream.scaladsl.Source
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.model.sse.ServerSentEvent
import sttp.tapir.{CodecFormat, Schema, StreamBodyIO, streamBody}

import java.nio.charset.Charset

package object akkahttp extends TapirAkkaHttpServer {
  val serverSentEventsBody: StreamBodyIO[Source[ByteString, Any], Source[ServerSentEvent, Any], AkkaStreams] =
    streamBody(AkkaStreams)(Schema.binary, CodecFormat.TextEventStream(), Some(Charset.forName("UTF-8")))
      .map(AkkaServerSentEvents.parseBytesToSSE)(AkkaServerSentEvents.serialiseSSEToBytes)
}
