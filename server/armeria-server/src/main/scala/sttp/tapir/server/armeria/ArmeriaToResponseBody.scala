package sttp.tapir.server.armeria

import com.linecorp.armeria.common.multipart.{BodyPart, Multipart}
import com.linecorp.armeria.common.stream.StreamMessage
import com.linecorp.armeria.common.{ContentDisposition, HttpData, HttpHeaders}
import com.linecorp.armeria.internal.shaded.guava.io.ByteStreams
import io.netty.buffer.Unpooled
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import sttp.capabilities.Streams
import sttp.model.{HasHeaders, HeaderNames, Part}
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, RawPart, WebSocketBodyOutput}

private[armeria] final class ArmeriaToResponseBody[S <: Streams[S]](streamCompatible: StreamCompatible[S])
    extends ToResponseBody[ArmeriaResponseType, S] {
  override val streams: S = streamCompatible.streams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): ArmeriaResponseType =
    rawValueToHttpData(bodyType, v)

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): ArmeriaResponseType =
    Left(StreamMessage.of(streamCompatible.asStreamMessage(v.asInstanceOf[streamCompatible.streams.BinaryStream])))

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]
  ): ArmeriaResponseType = throw new UnsupportedOperationException()

  private def rawValueToHttpData[R](bodyType: RawBodyType[R], v: R): ArmeriaResponseType = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val str = v.asInstanceOf[String]
        Right(HttpData.of(charset, str))

      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        Right(HttpData.wrap(bytes))

      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        Right(HttpData.wrap(Unpooled.wrappedBuffer(byteBuffer)))

      case RawBodyType.InputStreamBody =>
        val is = v.asInstanceOf[InputStream]
        // TODO(ikhoon): Add StreamMessage.of(InputStream)
        Right(HttpData.wrap(ByteStreams.toByteArray(is)))

      case RawBodyType.FileBody =>
        val tapirFile = v.asInstanceOf[FileRange]
        val streamMessage = tapirFile.range
          .flatMap(r =>
            r.startAndEnd.map { case (start, end) =>
              StreamMessage.of(tapirFile.file.toPath).range(start, end - start)
            }
          )
          .getOrElse(StreamMessage.of(tapirFile.file))
        Left(streamMessage)

      case m: RawBodyType.MultipartBody =>
        val parts = (v: Seq[RawPart]).flatMap(rawPartToBodyPart(m, _))
        Left(Multipart.of(parts: _*).toStreamMessage)
    }
  }

  private def rawPartToBodyPart[T](m: RawBodyType.MultipartBody, part: Part[T]): Option[BodyPart] = {
    m.partType(part.name).map { partType =>
      val headerBuilder = HttpHeaders.builder()
      part.headers.foreach(header => headerBuilder.add(header.name, header.value))

      if (part.header(HeaderNames.ContentDisposition).isEmpty) {
        // Build Content-Disposition header if missing
        val dispositionType = part.otherDispositionParams.getOrElse("type", "form-data")
        val dispositionBuilder =
          ContentDisposition
            .builder(dispositionType)
            .name(part.name)
        part.fileName.foreach(dispositionBuilder.filename)
        headerBuilder.contentDisposition(dispositionBuilder.build())
      }

      val bodyPartBuilder =
        BodyPart
          .builder()
          .headers(headerBuilder.build())
      rawValueToHttpData(partType.asInstanceOf[RawBodyType[Any]], part.body) match {
        case Left(stream) =>
          bodyPartBuilder.content(stream)
        case Right(httpData) =>
          bodyPartBuilder.content(httpData)
      }
      bodyPartBuilder.build();
    }
  }
}
