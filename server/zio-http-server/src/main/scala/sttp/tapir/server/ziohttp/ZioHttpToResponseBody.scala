package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.model.HasHeaders
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, WebSocketBodyOutput}
import zio.Chunk
import zio.stream.ZStream

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
    (ZStream.empty, None) // TODO

  private def rawValueToEntity[R](bodyType: RawBodyType[R], r: R): ZioHttpResponseBody = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = r.toString.getBytes(charset)
        (ZStream.fromIterable(bytes), Some(bytes.length.toLong))
      case RawBodyType.ByteArrayBody   => (ZStream.fromChunk(Chunk.fromArray(r)), Some((r: Array[Byte]).length.toLong))
      case RawBodyType.ByteBufferBody  => (ZStream.fromChunk(Chunk.fromByteBuffer(r)), None)
      case RawBodyType.InputStreamBody => (ZStream.fromInputStream(r), None)
      case RawBodyType.FileBody =>
        val tapirFile = r.asInstanceOf[FileRange]
        val stream = tapirFile.range
          .flatMap { r =>
            r.startAndEnd.map { s =>
              var count = 0L
              ZStream
                .fromPath(tapirFile.file.toPath)
                .dropWhile(_ =>
                  if (count < s._1) { count += 1; true }
                  else false
                )
                .take(r.contentLength)
            }
          }
          .getOrElse(ZStream.fromPath(tapirFile.file.toPath))
        (stream, Some(tapirFile.file.length))
      case RawBodyType.MultipartBody(_, _) => (ZStream.empty, None)
    }
  }
}
