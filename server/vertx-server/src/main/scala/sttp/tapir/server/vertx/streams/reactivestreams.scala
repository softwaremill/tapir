package sttp.tapir.server.vertx

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.ext.reactivestreams.{ReactiveReadStream, ReactiveWriteStream}
import org.reactivestreams.Publisher

package object streams {

  def reactiveStreamsReadStreamCompatible(vertx:Vertx): ReadStreamCompatible[ReactiveStreams] = new ReadStreamCompatible[ReactiveStreams] {

    override val streams: ReactiveStreams = ReactiveStreams

    override def asReadStream(stream: Publisher[Buffer]): ReadStream[Buffer] = {
      val readStream:ReactiveReadStream[Buffer] = ReactiveReadStream.readStream();
      stream.subscribe(readStream)
      readStream
    }

    override def fromReadStream(readStream: ReadStream[Buffer]): Publisher[Buffer] ={
      val writeStream: ReactiveWriteStream[Buffer] = ReactiveWriteStream.writeStream(vertx)
     writeStream.subscribe(readStream.asInstanceOf[ReactiveReadStream[Buffer]])
     writeStream
    }
  }
}
