package sttp.tapir.server.interpreter

import sttp.capabilities.Streams
import sttp.model.HasHeaders
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.Charset

trait ToResponseBody[B, S] {
  val streams: Streams[S]
  def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): B // TODO: remove headers?
  def fromStreamValue(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): B
  def fromWebSocketPipe[REQ, RESP](pipe: streams.Pipe[REQ, RESP], o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]): B
}
