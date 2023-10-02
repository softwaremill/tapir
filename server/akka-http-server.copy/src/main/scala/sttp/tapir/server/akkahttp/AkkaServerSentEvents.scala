package sttp.tapir.server.akkahttp

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.model.sse.ServerSentEvent

object AkkaServerSentEvents {

  def serialiseSSEToBytes: Source[ServerSentEvent, Any] => AkkaStreams.BinaryStream = sseStream =>
    sseStream.map(sse => {
      ByteString(s"${sse.toString()}\n\n")
    })

  def parseBytesToSSE: AkkaStreams.BinaryStream => Source[ServerSentEvent, Any] = stream => stream.via(parse)

  private val parse: Flow[ByteString, ServerSentEvent, NotUsed] =
    Framing
      .delimiter(ByteString("\n\n"), maximumFrameLength = Int.MaxValue, allowTruncation = true)
      .map(_.utf8String)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parse)

}
