package sttp.tapir.server.netty

import ox.Chunk
import ox.flow.Flow
import sttp.capabilities.Streams

trait NettyStreams extends Streams[NettyStreams] {
  override type BinaryStream = Flow[Chunk[Byte]]
  override type Pipe[A, B] = Flow[A] => Flow[B]
}

object NettyStreams extends NettyStreams
