package sttp.tapir.server.finatra

import com.twitter.io.{Buf, InputStreamReader, Reader}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.{FormBodyPart, FormBodyPartBuilder, MultipartEntityBuilder}
import org.apache.http.entity.mime.content.{ByteArrayBody, ContentBody, FileBody, InputStreamBody, StringBody}
import sttp.capabilities.Streams
import sttp.model.{HasHeaders, Header, Part}
import sttp.tapir.internal.NoStreams
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}
import sttp.tapir.server.interpreter.ToResponseBody

import java.io.InputStream
import java.nio.charset.Charset

class FinatraToResponseBody extends ToResponseBody[FinatraContent, NoStreams] {
  override val streams: NoStreams = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): FinatraContent = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        FinatraContentBuf(Buf.ByteArray.Owned(v.toString.getBytes(charset)))
      case RawBodyType.ByteArrayBody   => FinatraContentBuf(Buf.ByteArray.Owned(v))
      case RawBodyType.ByteBufferBody  => FinatraContentBuf(Buf.ByteBuffer.Owned(v))
      case RawBodyType.InputStreamBody => FinatraContentReader(Reader.fromStream(v))
      case RawBodyType.FileBody        => FinatraContentReader(Reader.fromFile(v))
      case m: RawBodyType.MultipartBody =>
        val entity = MultipartEntityBuilder.create()
        v.flatMap(rawPartToFormBodyPart(m, _)).foreach { formBodyPart: FormBodyPart => entity.addPart(formBodyPart) }

        // inputStream is split out into a val because otherwise it doesn't compile in 2.11
        val inputStream: InputStream = entity.build().getContent

        FinatraContentReader(InputStreamReader(inputStream))
    }
  }

  private def rawValueToContentBody[CF <: CodecFormat, R](bodyType: RawBodyType[R], part: Part[R], r: R): ContentBody = {
    val contentType: String = part.header("content-type").getOrElse("text/plain")

    bodyType match {
      case RawBodyType.StringBody(_) =>
        new StringBody(r.toString, ContentType.parse(contentType))
      case RawBodyType.ByteArrayBody =>
        new ByteArrayBody(r, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.ByteBufferBody =>
        val array: Array[Byte] = new Array[Byte](r.remaining)
        r.get(array)
        new ByteArrayBody(array, ContentType.create(contentType), part.fileName.get)
      case RawBodyType.FileBody =>
        part.fileName match {
          case Some(filename) => new FileBody(r, ContentType.create(contentType), filename)
          case None           => new FileBody(r, ContentType.create(contentType))
        }
      case RawBodyType.InputStreamBody =>
        new InputStreamBody(r, ContentType.create(contentType), part.fileName.get)
      case _: RawBodyType.MultipartBody =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
    }
  }

  private def rawPartToFormBodyPart[R](m: RawBodyType.MultipartBody, part: Part[R]): Option[FormBodyPart] = {
    m.partType(part.name).map { partType =>
      val builder = FormBodyPartBuilder
        .create(
          part.name,
          rawValueToContentBody(partType.asInstanceOf[RawBodyType[Any]], part.asInstanceOf[Part[Any]], part.body)
        )

      part.headers.foreach(header => builder.addField(header.name, header.value))

      builder.build()
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): FinatraContent = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): FinatraContent = throw new UnsupportedOperationException
}
