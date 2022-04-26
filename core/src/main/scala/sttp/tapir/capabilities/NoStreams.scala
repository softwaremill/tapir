package sttp.tapir.capabilities

import sttp.capabilities.Streams

trait NoStreams extends Streams[NoStreams] {
  override type BinaryStream = Nothing
  override type Pipe[A, B] = Nothing
}
object NoStreams extends NoStreams
