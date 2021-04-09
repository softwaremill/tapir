package sttp.tapir.server.http4s

import fs2.{Stream, text}
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.sse.ServerSentEvent

object Http4sServerSentEvents {

  def serialiseSSEToBytes[F[_]](streams: Fs2Streams[F]): Stream[F, ServerSentEvent] => streams.BinaryStream = sseStream => {
    sseStream
      .map(sse => {
        s"${sse.toString()}\n\n"
      })
      .through(text.utf8Encode)
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
