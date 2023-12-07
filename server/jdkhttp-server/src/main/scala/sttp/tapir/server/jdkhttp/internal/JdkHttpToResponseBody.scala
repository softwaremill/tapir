package sttp.tapir.server.jdkhttp
package internal

import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.content._
import org.apache.http.entity.mime.{FormBodyPart, FormBodyPartBuilder, MultipartEntityBuilder}
import sttp.capabilities
import sttp.model.{HasHeaders, Part}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, InputStreamRange, RawBodyType, WebSocketBodyOutput}

import java.io.{ByteArrayInputStream, FileInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

private[jdkhttp] class JdkHttpToResponseBody extends ToResponseBody[JdkHttpResponseBody, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): JdkHttpResponseBody = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = v.asInstanceOf[String].getBytes(charset)
        (new ByteArrayInputStream(bytes), Some(bytes.length.toLong))
      case RawBodyType.ByteArrayBody =>
        val arr = v.asInstanceOf[Array[Byte]]
        (new ByteArrayInputStream(arr), Some(arr.length.toLong))
      case RawBodyType.ByteBufferBody =>
        (new ByteBufferBackedInputStream(v.asInstanceOf[ByteBuffer]), None) // TODO can't we provide the length?
      case RawBodyType.InputStreamBody =>
        (v.asInstanceOf[InputStream], None)
      case RawBodyType.InputStreamRangeBody =>
        v.asInstanceOf[InputStreamRange]
          .range
          .map { range =>
            val is = v.asInstanceOf[InputStreamRange].inputStreamFromRangeStart()
            (new LimitedInputStream(is, range.contentLength), Some(range.contentLength))
          }
          .getOrElse {
            (v.asInstanceOf[InputStreamRange].inputStream(), None)
          }
      case RawBodyType.FileBody =>
        val tapirFile = v.asInstanceOf[FileRange]
        val base = new FileInputStream(tapirFile.file)
        tapirFile.range
          .flatMap { r =>
            r.startAndEnd.map { case (start, end) =>
              base.skip(start)
              (new LimitedInputStream(base, r.contentLength), Some(r.contentLength))
            }
          }
          .getOrElse {
            (base, Some(tapirFile.file.length()))
          }
      case m: RawBodyType.MultipartBody =>
        val entity = MultipartEntityBuilder.create()
        v.flatMap(rawPartToFormBodyPart(m, _)).foreach { (formBodyPart: FormBodyPart) => entity.addPart(formBodyPart) }
        val builtEntity = entity.build()
        val inputStream: InputStream = builtEntity.getContent
        (inputStream, Some(builtEntity.getContentLength))
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

  private def rawValueToContentBody[CF <: CodecFormat, R](
      bodyType: RawBodyType[R],
      part: Part[R],
      r: R
  ): ContentBody = {
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
          case Some(filename) => new FileBody(r.file, ContentType.create(contentType), filename)
          case None           => new FileBody(r.file, ContentType.create(contentType))
        }
      case RawBodyType.InputStreamRangeBody =>
        new InputStreamBody(r.inputStream(), ContentType.create(contentType), part.fileName.get)
      case RawBodyType.InputStreamBody =>
        new InputStreamBody(r, ContentType.create(contentType), part.fileName.get)
      case _: RawBodyType.MultipartBody =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): JdkHttpResponseBody = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): JdkHttpResponseBody = throw new UnsupportedOperationException
}

// https://stackoverflow.com/questions/4332264/wrapping-a-bytebuffer-with-an-inputstream
private class ByteBufferBackedInputStream(buf: ByteBuffer) extends InputStream {
  override def read: Int = {
    if (!buf.hasRemaining) return -1
    buf.get & 0xff
  }

  override def read(bytes: Array[Byte], off: Int, len: Int): Int = {
    if (!buf.hasRemaining) return -1
    val len2 = Math.min(len, buf.remaining)
    buf.get(bytes, off, len2)
    len2
  }
}

