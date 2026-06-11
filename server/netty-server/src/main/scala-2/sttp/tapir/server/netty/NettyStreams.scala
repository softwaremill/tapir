package sttp.tapir.server.netty

import sttp.capabilities.Streams
// Copied from NoStreams
trait NettyStreams extends Streams[NettyStreams] {
  override type BinaryStream = Nothing
  override type Pipe[A, B] = Nothing
}

object NettyStreams extends NettyStreams
