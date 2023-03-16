package sttp.tapir.server.vertx

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.tapir.WebSocketBodyOutput
import sttp.ws.WebSocketFrame

package object streams {

  def reactiveStreamsReadStreamCompatible(): ReadStreamCompatible[VertxStreams] = new ReadStreamCompatible[VertxStreams] {

    override val streams: VertxStreams = VertxStreams

    override def asReadStream(readStream: ReadStream[Buffer]): ReadStream[Buffer] =
      readStream

    override def fromReadStream(readStream: ReadStream[Buffer]): ReadStream[Buffer] =
      readStream

    override def webSocketPipe[REQ, RESP](
      readStream: ReadStream[WebSocketFrame],
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, VertxStreams]
    ): ReadStream[WebSocketFrame] =
      throw new UnsupportedOperationException()
  }
}
