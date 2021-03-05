package sttp.tapir.server.internal

import sttp.capabilities.Streams
import sttp.model.HasHeaders
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.nio.charset.Charset

trait RawToResponseBody[WB, B, S] {
  val streams: Streams[S]
  def rawValueToBody[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): B
  def streamValueToBody(v: streams.BinaryStream, headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): B
  def webSocketPipeToBody[REQ, RESP](pipe: streams.Pipe[REQ, RESP], o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]): WB
}
