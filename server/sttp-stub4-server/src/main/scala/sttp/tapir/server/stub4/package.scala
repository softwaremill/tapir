package sttp.tapir.server

import sttp.capabilities.Streams

package object stub4 extends SttpStubServer {

  private[stub4] trait AnyStreams extends Streams[AnyStreams] {
    override type BinaryStream = Any
    override type Pipe[A, B] = Nothing
  }
  private[stub4] object AnyStreams extends AnyStreams
}
