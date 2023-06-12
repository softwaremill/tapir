package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.model.HasHeaders
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, WebSocketBodyOutput}
import zio.Chunk
import zio.blocking.Blocking
import zio.stream.{Stream, ZStream}

import java.nio.charset.Charset

class ZioHttpToResponseBody extends ToResponseBody[ZioHttpResponseBody, ZioStreams] {
  override val streams: ZioStreams = ZioStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): ZioHttpResponseBody =
    rawValueToEntity(bodyType, v)

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): ZioHttpResponseBody = (v, None)

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): ZioHttpResponseBody =
    (Stream.empty, None) // TODO

  private def rawValueToEntity[R](bodyType: RawBodyType[R], r: R): ZioHttpResponseBody = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = r.toString.getBytes(charset)
        (ZStream.fromIterable(bytes), Some(bytes.length.toLong))
      case RawBodyType.ByteArrayBody   => (Stream.fromChunk(Chunk.fromArray(r)), Some((r: Array[Byte]).length.toLong))
      case RawBodyType.ByteBufferBody  => (Stream.fromChunk(Chunk.fromByteBuffer(r)), None)
      case RawBodyType.InputStreamBody => (ZStream.fromInputStream(r).provideLayer(Blocking.live), None)
      case RawBodyType.InputStreamRangeBody =>
        r.range
          .map(range =>
            (
              ZStream.fromInputStream(r.inputStreamFromRangeStart()).take(range.contentLength).provideLayer(Blocking.live),
              Some(range.contentLength)
            )
          )
          .getOrElse((ZStream.fromInputStream(r.inputStream()).provideLayer(Blocking.live), None))
      case RawBodyType.FileBody =>
        val tapirFile = r: FileRange
        tapirFile.range
          .flatMap(r =>
            r.startAndEnd.map(s =>
              (ZStream.fromFile(tapirFile.file.toPath).drop(s._1).take(r.contentLength).provideLayer(Blocking.live), Some(r.contentLength))
            )
          )
          .getOrElse((ZStream.fromFile(tapirFile.file.toPath).provideLayer(Blocking.live), Some(tapirFile.file.length)))
      case RawBodyType.MultipartBody(_, _) => throw new UnsupportedOperationException("Multipart is not supported")
    }
  }
}
