package sttp.tapir.server.netty.sync

import ox.flow.Flow
import sttp.capabilities.Streams

trait OxStreams extends Streams[OxStreams]:
  override type BinaryStream = Nothing
  override type Pipe[A, B] = Flow[A] => Flow[B]

object OxStreams extends OxStreams
