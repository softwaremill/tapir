package sttp.tapir.server.netty.loom

import ox.Ox
import ox.channels.Source
import sttp.capabilities.Streams

trait OxStreams extends Streams[OxStreams] {
  override type BinaryStream = Nothing
  override type Pipe[A, B] = Ox ?=> Source[A] => Source[B]
}

object OxStreams extends OxStreams
