package sttp.tapir.server.nima.internal

import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.io.{ByteArrayInputStream, FileInputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset

private[nima] class NimaToResponseBody extends ToResponseBody[InputStream, NoStreams] {

  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): InputStream = {
    bodyType match {
      case RawBodyType.StringBody(charset)       => new ByteArrayInputStream(v.getBytes(charset))
      case RawBodyType.ByteArrayBody             => new ByteArrayInputStream(v)
      case RawBodyType.ByteBufferBody            => new ByteBufferBackedInputStream(v)
      case RawBodyType.InputStreamBody           => v
      case rr @ RawBodyType.InputStreamRangeBody =>
        val base = v.inputStreamFromRangeStart()
        v.range.flatMap(_.startAndEnd) match {
          case Some((start, end)) =>
            new LimitedInputStream(base, end - start)
          case None => base
        }
      case RawBodyType.FileBody =>
        val base = new FileInputStream(v.file)
        v.range.flatMap(_.startAndEnd) match {
          case Some((start, end)) =>
            base.skip(start)
            new LimitedInputStream(base, end - start)
          case None => base
        }
      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): InputStream = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): InputStream = throw new UnsupportedOperationException
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
