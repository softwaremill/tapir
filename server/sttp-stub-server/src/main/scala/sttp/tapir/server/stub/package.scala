package sttp.tapir.server

import sttp.capabilities.Streams

package object stub extends SttpStubServer {

  private[stub] trait AnyStreams extends Streams[AnyStreams] {
    override type BinaryStream = Any
    override type Pipe[A, B] = Nothing
  }
  private[stub] object AnyStreams extends AnyStreams
}
