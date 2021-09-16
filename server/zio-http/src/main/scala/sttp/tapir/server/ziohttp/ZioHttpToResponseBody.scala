package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.model.HasHeaders
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}
import zio.Chunk
import zio.blocking.Blocking
import zio.stream.{Stream, ZStream}

import java.nio.charset.Charset

class ZioHttpToResponseBody extends ToResponseBody[ZStream[Any, Throwable, Byte], ZioStreams] {
  override val streams: ZioStreams = ZioStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): ZStream[Any, Throwable, Byte] =
    rawValueToEntity(bodyType, v)

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): ZStream[Any, Throwable, Byte] = v

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): ZStream[Any, Throwable, Byte] =
    Stream.empty //TODO

  private def rawValueToEntity[CF <: CodecFormat, R](bodyType: RawBodyType[R], r: R): ZStream[Any, Throwable, Byte] = {
    bodyType match {
      case RawBodyType.StringBody(charset) => ZStream.fromIterable(r.toString.getBytes(charset))
      case RawBodyType.ByteArrayBody       => Stream.fromChunk(Chunk.fromArray(r))
      case RawBodyType.ByteBufferBody      => Stream.fromChunk(Chunk.fromByteBuffer(r))
      case RawBodyType.InputStreamBody     => ZStream.fromInputStream(r).provideLayer(Blocking.live)
      case RawBodyType.FileBody            => ZStream.fromFile(r.toPath).provideLayer(Blocking.live)
      case RawBodyType.MultipartBody(_, _) => Stream.empty
    }
  }
}
