package sttp.tapir.server.http4s

import fs2.{Stream, text}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent

object Http4sServerSentEvents {

  def serialiseSSEToBytes[F[_]](streams: Fs2Streams[F]): Stream[F, ServerSentEvent] => streams.BinaryStream = sseStream => {
    sseStream
      .map(sse => {
        s"${composeSSE(sse)}\n\n"
      })
      .through(text.utf8Encode)
  }

  private def composeSSE(sse: ServerSentEvent) = {
    val data = sse.data.map(_.split("\n")).map(_.map(line => Some(s"data: $line"))).getOrElse(Array.empty)
    val event = sse.eventType.map(event => s"event: $event")
    val id = sse.id.map(id => s"id: $id")
    val retry = sse.retry.map(retryCount => s"retry: $retryCount")
    (data :+ event :+ id :+ retry).flatten.mkString("\n")
  }

  def parseBytesToSSE[F[_]](streams: Fs2Streams[F]): streams.BinaryStream => Stream[F, ServerSentEvent] = stream => {
    stream
      .through(text.utf8Decode[F])
      .through(text.lines[F])
      .split(_.isEmpty)
      .filter(_.nonEmpty)
      .map(_.toList)
      .map(ServerSentEvent.parse)
  }

}
