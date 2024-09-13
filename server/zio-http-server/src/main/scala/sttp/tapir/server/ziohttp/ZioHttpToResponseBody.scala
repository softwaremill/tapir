package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.model.HasHeaders
import sttp.model.Part
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.CodecFormat
import sttp.tapir.RawBodyType
import sttp.tapir.WebSocketBodyOutput
import zio.Chunk
import zio.http.FormField
import zio.http.MediaType
import zio.stream.ZStream

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

class ZioHttpToResponseBody extends ToResponseBody[ZioResponseBody, ZioStreams] {
  override val streams: ZioStreams = ZioStreams

  override def fromRawValue[R](
      v: R,
      headers: HasHeaders,
      format: CodecFormat,
      bodyType: RawBodyType[R]
  ): ZioResponseBody =
    Right(rawValueToEntity(bodyType, v))

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): ZioResponseBody = Right(ZioStreamHttpResponseBody(v, None))

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): ZioResponseBody =
    Left(ZioWebSockets.pipeToBody(pipe, o))

  private def rawValueToEntity[R](bodyType: RawBodyType[R], r: R): ZioHttpResponseBody =
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = r.toString.getBytes(charset)
        ZioRawHttpResponseBody(Chunk.fromArray(bytes), Some(bytes.length.toLong))
      case RawBodyType.ByteArrayBody =>
        ZioRawHttpResponseBody(Chunk.fromArray(r), Some((r: Array[Byte]).length.toLong))
      case RawBodyType.ByteBufferBody =>
        val buffer: ByteBuffer = r
        ZioRawHttpResponseBody(Chunk.fromByteBuffer(buffer), Some(buffer.remaining()))
      case RawBodyType.InputStreamBody =>
        ZioStreamHttpResponseBody(ZStream.fromInputStream(r), None)
      case RawBodyType.InputStreamRangeBody =>
        r.range
          .map(range =>
            ZioStreamHttpResponseBody(
              ZStream.fromInputStream(r.inputStreamFromRangeStart()).take(range.contentLength),
              Some(range.contentLength)
            )
          )
          .getOrElse(ZioStreamHttpResponseBody(ZStream.fromInputStream(r.inputStream()), None))
      case RawBodyType.FileBody =>
        val tapirFile = r
        tapirFile.range
          .flatMap { r =>
            r.startAndEnd.map { s =>
              var count = 0L
              ZioStreamHttpResponseBody(
                ZStream
                  .fromPath(tapirFile.file.toPath)
                  .dropWhile(_ =>
                    if (count < s._1) { count += 1; true }
                    else false
                  )
                  .take(r.contentLength),
                Some(r.contentLength)
              )
            }
          }
          .getOrElse(ZioStreamHttpResponseBody(ZStream.fromPath(tapirFile.file.toPath), Some(tapirFile.file.length)))
      case RawBodyType.MultipartBody(_, _) => throw new UnsupportedOperationException("Multipart is not supported")
    }

  def toFormField[CF <: CodecFormat, R](bodyType: RawBodyType[R], part: Part[R], r: R): FormField = {
    val mediaType: Option[MediaType] = part.contentType.flatMap(MediaType.forContentType)
    bodyType match {
      case RawBodyType.StringBody(_) =>
        FormField.Text(part.name, part.body, mediaType.getOrElse(MediaType.text.plain), part.fileName)
      case RawBodyType.ByteArrayBody =>
        FormField.Binary(
          part.name,
          Chunk.fromArray(part.body),
          mediaType.getOrElse(MediaType.application.`octet-stream`),
          filename = part.fileName
        )
      case RawBodyType.ByteBufferBody =>
        val array: Array[Byte] = new Array[Byte](r.remaining)
        r.get(array)
        FormField.Binary(
          part.name,
          Chunk.fromArray(array),
          mediaType.getOrElse(MediaType.application.`octet-stream`),
          filename = part.fileName
        )
      case RawBodyType.FileBody =>
        FormField.streamingBinaryField(
          part.name,
          ZStream.fromFile(r.file).orDie,
          mediaType.getOrElse(MediaType.application.`octet-stream`),
          filename = part.fileName
        )
      case RawBodyType.InputStreamBody =>
        FormField.streamingBinaryField(
          part.name,
          ZStream.fromInputStream(r).orDie,
          mediaType.getOrElse(MediaType.application.`octet-stream`),
          filename = part.fileName
        )
      case RawBodyType.InputStreamRangeBody =>
        FormField.streamingBinaryField(
          part.name,
          ZStream.fromInputStream(r.inputStream()).orDie,
          mediaType.getOrElse(MediaType.application.`octet-stream`),
          filename = part.fileName
        )
      case _: RawBodyType.MultipartBody =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
    }
  }
}
