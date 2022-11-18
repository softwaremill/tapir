package sttp.tapir.server.vertx

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream

package object streams {

  def reactiveStreamsReadStreamCompatible(): ReadStreamCompatible[VertxStreams] = new ReadStreamCompatible[VertxStreams] {

    override val streams: VertxStreams = VertxStreams

    override def asReadStream(readStream: ReadStream[Buffer]): ReadStream[Buffer] =
      readStream

    override def fromReadStream(readStream: ReadStream[Buffer]): ReadStream[Buffer] =
      readStream
  }
}
