package sttp.tapir.server.vertx.streams

import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import sttp.capabilities.Streams
import sttp.tapir.WebSocketBodyOutput
import sttp.ws.WebSocketFrame

trait ReadStreamCompatible[S <: Streams[S]] {
  val streams: S
  def asReadStream(s: streams.BinaryStream): ReadStream[Buffer]
  def fromReadStream(s: ReadStream[Buffer], maxBytes: Option[Long]): streams.BinaryStream

  def webSocketPipe[REQ, RESP](
      readStream: ReadStream[WebSocketFrame],
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]
  ): ReadStream[WebSocketFrame]
}

object ReadStreamCompatible {
  def apply[S <: Streams[S]](implicit ev: ReadStreamCompatible[S]): ReadStreamCompatible[S] = ev
}

trait VertxStreams extends Streams[VertxStreams] {
  override type BinaryStream = ReadStream[Buffer]
  override type Pipe[A, B] = ReadStream[A] => ReadStream[B]
}

object VertxStreams extends VertxStreams
