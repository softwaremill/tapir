package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpChunkedInput
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, WebSocketBodyOutput}

import java.io.{ByteArrayInputStream, InputStream, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.charset.Charset

class NettyToResponseBody extends ToResponseBody[HttpChunkedInput, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): HttpChunkedInput = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = v.asInstanceOf[String].getBytes(charset)
        wrap(bytes)

      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        wrap(bytes)

      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        wrap(byteBuffer.array())

      case RawBodyType.InputStreamBody =>
        val stream = v.asInstanceOf[InputStream]
        wrap(stream)

      case RawBodyType.FileBody         =>
        val fileRange = v.asInstanceOf[FileRange]
        wrap(fileRange)

      case _: RawBodyType.MultipartBody => ???
    }
  }

  private def wrap(content: Array[Byte]): HttpChunkedInput = {
    new HttpChunkedInput(new ChunkedStream(new ByteArrayInputStream(content)))
  }

  private def wrap(content: InputStream): HttpChunkedInput = {
    new HttpChunkedInput(new ChunkedStream(content))
  }

  private def wrap(content: FileRange): HttpChunkedInput = {
    val file = content.file
    val maybeRange = for {
      range <- content.range
      start <- range.start
      end <- range.end
    } yield (start, end + NettyToResponseBody.IncludingLastOffset)

    maybeRange match {
      case Some((start, end)) => {
        val randomAccessFile = new RandomAccessFile(file, NettyToResponseBody.ReadOnlyAccessMode)
        new HttpChunkedInput(new ChunkedFile(randomAccessFile, start, end - start, NettyToResponseBody.DefaultChunkSize))
      }
      case None => new HttpChunkedInput(new ChunkedFile(file))
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): HttpChunkedInput = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): HttpChunkedInput = throw new UnsupportedOperationException
}

object NettyToResponseBody {
  private val DefaultChunkSize = 8192
  private val IncludingLastOffset = 1
  private val ReadOnlyAccessMode = "r"
}
