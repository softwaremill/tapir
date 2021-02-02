package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.Streams
import sttp.tapir.internal.NoStreams

trait ReadStreamCompatible[S] {

  type Capability <: Streams[S]

  val streams: Capability

  def asReadStream(s: Capability#BinaryStream): ReadStream[Buffer]

  def fromReadStream(s: ReadStream[Buffer]): Capability#BinaryStream
}

object ReadStreamCompatible {

  def apply[S](implicit ev: ReadStreamCompatible[S]): ReadStreamCompatible[S] =
    ev

  implicit val incompatible: ReadStreamCompatible[Nothing] = new ReadStreamCompatible[Nothing] {
    override type Capability = NoStreams

    override val streams = NoStreams

    override def asReadStream(s: Capability#BinaryStream): ReadStream[Buffer] =
      ???

    override def fromReadStream(s: ReadStream[Buffer]): Capability#BinaryStream =
      ???
  }
}
