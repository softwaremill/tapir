package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBuf, Unpooled}
import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, WebSocketBodyOutput}

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files

class NettyToResponseBody extends ToResponseBody[ByteBuf, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): ByteBuf = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        Unpooled.wrappedBuffer(v.toString.getBytes(charset))
      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        Unpooled.wrappedBuffer(bytes)
      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        Unpooled.wrappedBuffer(byteBuffer)

      case RawBodyType.InputStreamBody =>
        val stream = v.asInstanceOf[InputStream]
        Unpooled.wrappedBuffer(stream.readAllBytes())

      case RawBodyType.FileBody         =>
        val fileRange = v.asInstanceOf[FileRange]
        val bytes = (for {
          range <- fileRange.range
          start <- range.start
          end <- range.end
        } yield Files.readAllBytes(fileRange.file.toPath).slice(start.toInt, end.toInt + NettyToResponseBody.IncludingLastOffset))
          .getOrElse(Files.readAllBytes(fileRange.file.toPath))
        Unpooled.wrappedBuffer(bytes)

      case _: RawBodyType.MultipartBody => ???
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): ByteBuf = throw new UnsupportedOperationException

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, NoStreams]
  ): ByteBuf = throw new UnsupportedOperationException
}

object NettyToResponseBody {
  private val IncludingLastOffset = 1
}
