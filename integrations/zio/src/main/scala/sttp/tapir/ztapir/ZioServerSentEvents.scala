package sttp.tapir.ztapir

import java.nio.charset.StandardCharsets

import zio.stream.{Stream, ZPipeline}
import sttp.model.sse.ServerSentEvent
import zio.Chunk

object ZioServerSentEvents {
  def serialiseSSEToBytes: Stream[Throwable, ServerSentEvent] => Stream[Throwable, Byte] = sseStream => {
    sseStream
      .map(sse => {
        s"${sse.toString()}\n\n"
      })
      .mapConcatChunk(s => Chunk.fromArray(s.getBytes(StandardCharsets.UTF_8)))
  }

  def parseBytesToSSE: Stream[Throwable, Byte] => Stream[Throwable, ServerSentEvent] = stream => {
    stream
      .via(ZPipeline.utf8Decode)
      .via(ZPipeline.splitLines)
      .mapAccum(List.empty[String]) { case (acc, line) =>
        if (line.isEmpty) (Nil, Some(acc.reverse))
        else (line :: acc, None)
      }
      .collect { case Some(l) =>
        l
      }
      .filter(_.nonEmpty)
      .map(ServerSentEvent.parse)
  }
}
