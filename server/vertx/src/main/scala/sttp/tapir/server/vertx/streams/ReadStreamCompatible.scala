package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.Streams
import sttp.tapir.internal.NoStreams

trait ReadStreamCompatible[S, BS] {
  val streams: Streams[S]
  def asReadStream(s: BS): ReadStream[Buffer]
  def fromReadStream(s: ReadStream[Buffer]): BS
}

object ReadStreamCompatible {
  def apply[S, BS](implicit ev: ReadStreamCompatible[S, BS]): ReadStreamCompatible[S, BS] = ev

  implicit val incompatible: ReadStreamCompatible[Nothing, Nothing] = new ReadStreamCompatible[Nothing, Nothing] {
    override val streams: NoStreams = NoStreams
    override def asReadStream(s: Nothing): ReadStream[Buffer] = ???
    override def fromReadStream(s: ReadStream[Buffer]): Nothing = ???
  }
}
