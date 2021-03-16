package sttp.tapir.server.akkahttp

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString
import sttp.capabilities.akka.AkkaStreams
import sttp.model.sse.ServerSentEvent

object AkkaServerSentEvents {

  def serialiseSSEToBytes: Source[ServerSentEvent, Any] => AkkaStreams.BinaryStream = sseStream =>
    sseStream.map(sse => {
      ByteString(s"${composeSSE(sse)}\n\n")
    })

  private def composeSSE(sse: ServerSentEvent) = {
    val data = sse.data.map(_.split("\n")).map(_.map(line => Some(s"data: $line"))).getOrElse(Array.empty)
    val event = sse.eventType.map(event => s"event: $event")
    val id = sse.id.map(id => s"id: $id")
    val retry = sse.retry.map(retryCount => s"retry: $retryCount")
    (data :+ event :+ id :+ retry).flatten.mkString("\n")
  }

  def parseBytesToSSE: AkkaStreams.BinaryStream => Source[ServerSentEvent, Any] = stream => stream.via(parse)

  private val parse: Flow[ByteString, ServerSentEvent, NotUsed] =
    Framing
      .delimiter(ByteString("\n\n"), maximumFrameLength = Int.MaxValue, allowTruncation = true)
      .map(_.utf8String)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parse)

}
