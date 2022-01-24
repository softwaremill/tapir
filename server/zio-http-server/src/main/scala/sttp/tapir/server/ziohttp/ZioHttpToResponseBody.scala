package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.model.HasHeaders
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, WebSocketBodyOutput}
import zio.Chunk
import zio.blocking.Blocking
import zio.stream.Stream

import java.nio.charset.Charset

class ZioHttpToResponseBody extends ToResponseBody[ZioResponseBody, ZioStreams] {
  override val streams: ZioStreams = ZioStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): ZioResponseBody =
    rawValueToEntity(bodyType, v)

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): ZioResponseBody = Right(v)

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): ZioResponseBody =
    Left(ZioWebSockets.pipeToBody(pipe, o))

  private def rawValueToEntity[CF <: CodecFormat, R](bodyType: RawBodyType[R], r: R): ZioResponseBody = Right {
    bodyType match {
      case RawBodyType.StringBody(charset) => Stream.fromIterable(r.toString.getBytes(charset))
      case RawBodyType.ByteArrayBody       => Stream.fromChunk(Chunk.fromArray(r))
      case RawBodyType.ByteBufferBody      => Stream.fromChunk(Chunk.fromByteBuffer(r))
      case RawBodyType.InputStreamBody     => Stream.fromInputStream(r).provideLayer(Blocking.live)
      case RawBodyType.FileBody =>
        val tapirFile = r.asInstanceOf[FileRange]
        tapirFile.range
          .flatMap(r =>
            r.startAndEnd.map(s => Stream.fromFile(tapirFile.file.toPath).drop(s._1).take(r.contentLength).provideLayer(Blocking.live))
          )
          .getOrElse(Stream.fromFile(tapirFile.file.toPath).provideLayer(Blocking.live))
      case RawBodyType.MultipartBody(_, _) => Stream.empty
    }
  }
}
