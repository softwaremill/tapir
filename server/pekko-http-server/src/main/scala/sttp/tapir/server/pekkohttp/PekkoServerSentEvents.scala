package sttp.tapir.server.pekkohttp

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Framing, Source}
import org.apache.pekko.util.ByteString
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.sse.ServerSentEvent

object PekkoServerSentEvents {

  def serialiseSSEToBytes: Source[ServerSentEvent, Any] => PekkoStreams.BinaryStream = sseStream =>
    sseStream.map(sse => {
      ByteString(s"${sse.toString()}\n\n")
    })

  def parseBytesToSSE: PekkoStreams.BinaryStream => Source[ServerSentEvent, Any] = stream => stream.via(parse)

  private val parse: Flow[ByteString, ServerSentEvent, NotUsed] =
    Framing
      .delimiter(ByteString("\n\n"), maximumFrameLength = Int.MaxValue, allowTruncation = true)
      .map(_.utf8String)
      .map(_.split("\n").toList)
      .map(ServerSentEvent.parse)

}
