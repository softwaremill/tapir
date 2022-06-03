package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.Streams
import sttp.tapir.capabilities.NoStreams

trait ReadStreamCompatible[S <: Streams[S]] {
  val streams: S
  def asReadStream(s: streams.BinaryStream): ReadStream[Buffer]
  def fromReadStream(s: ReadStream[Buffer]): streams.BinaryStream
}

object ReadStreamCompatible {
  def apply[S <: Streams[S]](implicit ev: ReadStreamCompatible[S]): ReadStreamCompatible[S] = ev

  val incompatible: ReadStreamCompatible[NoStreams] = new ReadStreamCompatible[NoStreams] {
    override val streams: NoStreams = NoStreams
    override def asReadStream(s: Nothing): ReadStream[Buffer] = ???
    override def fromReadStream(s: ReadStream[Buffer]): Nothing = ???
  }
}
