package sttp.tapir.server.netty

import sttp.model.sse.ServerSentEvent
import ox.flow.Flow
import ox.Chunk

object OxServerSentEvents:
  def serializeSSEToBytes: Flow[ServerSentEvent] => Flow[Chunk[Byte]] = sseStream =>
    sseStream.map(sse => Chunk.fromArray(s"${sse.toString()}\n\n".getBytes("UTF-8")))

  def parseBytesToSSE: Flow[Chunk[Byte]] => Flow[ServerSentEvent] = stream =>
    stream.linesUtf8
      .mapStatefulConcat(Vector.empty[String]) { (previousLines, line) =>
        if line.isEmpty then (Vector.empty[String], List(previousLines)) else (previousLines :+ line, Nil)
      }
      .filter(_.nonEmpty)
      .map(_.toList)
      .map(ServerSentEvent.parse)
