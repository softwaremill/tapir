package sttp.tapir.server.http4s

import fs2.{Stream, text}
import sttp.model.sse.ServerSentEvent

object Http4sServerSentEvents {

  def serialiseSSEToBytes[F[_]]: Stream[F, ServerSentEvent] => Stream[F, Byte] = sseStream => {
    sseStream
      .map(sse => {
        s"${sse.toString()}\n\n"
      })
      .through(text.utf8.encode)
  }

  def parseBytesToSSE[F[_]]: Stream[F, Byte] => Stream[F, ServerSentEvent] = stream => {
    stream
      .through(text.utf8.decode[F])
      .through(text.lines[F])
      .split(_.isEmpty)
      .filter(_.nonEmpty)
      .map(_.toList)
      .map(ServerSentEvent.parse)
  }

}
