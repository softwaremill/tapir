package sttp.tapir.server.netty.sync

import ox.Chunk
import ox.flow.Flow
import sttp.capabilities.Streams

trait OxStreams extends Streams[OxStreams]:
  override type BinaryStream = Flow[Chunk[Byte]]
  override type Pipe[A, B] = Flow[A] => Flow[B]

object OxStreams extends OxStreams
