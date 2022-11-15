package sttp.tapir.server.netty.internal

import io.netty.handler.stream.ChunkedStream
import sttp.capabilities.Streams

trait NettyStreams extends Streams[NettyStreams] {
  type BinaryStream = ChunkedStream
  type Pipe[A, B] = ChunkedStream => ChunkedStream
}

object NettyStreams extends NettyStreams
