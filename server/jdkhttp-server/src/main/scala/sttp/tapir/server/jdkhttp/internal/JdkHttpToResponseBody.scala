package sttp.tapir.server.jdkhttp
package internal

import sttp.capabilities
import sttp.model.HasHeaders
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
      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException("MultipartBody is not supported")
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

private class LimitedInputStream(delegate: InputStream, var limit: Long) extends InputStream {
  override def read(): Int = {
    if (limit == 0L) -1
    else {
      limit -= 1
      delegate.read()
    }
  }
}
