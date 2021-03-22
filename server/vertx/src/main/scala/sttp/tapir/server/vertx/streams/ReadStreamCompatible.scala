package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.Streams
import sttp.tapir.internal.NoStreams

trait ReadStreamCompatible[S] {
  val streams: Streams[S]
  def asReadStream(s: streams.BinaryStream): ReadStream[Buffer]
  def fromReadStream(s: ReadStream[Buffer]): streams.BinaryStream
}

object ReadStreamCompatible {
  def apply[S](implicit ev: ReadStreamCompatible[S]): ReadStreamCompatible[S] = ev

  implicit val incompatible: ReadStreamCompatible[Nothing] = new ReadStreamCompatible[Nothing] {
    override val streams: NoStreams = NoStreams
    override def asReadStream(s: streams.BinaryStream): ReadStream[Buffer] = ???
    override def fromReadStream(s: ReadStream[Buffer]): streams.BinaryStream = ???
  }
}
