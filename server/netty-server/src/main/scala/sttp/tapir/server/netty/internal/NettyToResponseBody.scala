package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpChunkedInput
import io.netty.handler.stream.ChunkedFile
import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, WebSocketBodyOutput}

import java.io.{InputStream, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.charset.Charset

class NettyToResponseBody extends ToResponseBody[NettyResponse, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = v.asInstanceOf[String].getBytes(charset)
        Left(Unpooled.wrappedBuffer(bytes))

      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        Left(Unpooled.wrappedBuffer(bytes))

      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        Left(Unpooled.wrappedBuffer(byteBuffer.array()))

      case RawBodyType.InputStreamBody =>
        val stream = v.asInstanceOf[InputStream]
        Left(Unpooled.wrappedBuffer(stream.readAllBytes()))

      case RawBodyType.FileBody =>
        val fileRange = v.asInstanceOf[FileRange]
        Right(wrap(fileRange))

      case _: RawBodyType.MultipartBody => ???
    }
  }

  private def wrap(content: FileRange): (HttpChunkedInput, Long) = {
    val file = content.file
    val maybeRange = for {
      range <- content.range
      start <- range.start
      end <- range.end
    } yield (start, end + NettyToResponseBody.IncludingLastOffset)

    maybeRange match {
      case Some((start, end)) => {
        val randomAccessFile = new RandomAccessFile(file, NettyToResponseBody.ReadOnlyAccessMode)
        (new HttpChunkedInput(new ChunkedFile(randomAccessFile, start, end - start, NettyToResponseBody.DefaultChunkSize)), end - start)
      }
      case None => (new HttpChunkedInput(new ChunkedFile(file)), file.length())
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): NettyResponse = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): NettyResponse = throw new UnsupportedOperationException
}

object NettyToResponseBody {
  private val DefaultChunkSize = 8192
  private val IncludingLastOffset = 1
  private val ReadOnlyAccessMode = "r"
}
