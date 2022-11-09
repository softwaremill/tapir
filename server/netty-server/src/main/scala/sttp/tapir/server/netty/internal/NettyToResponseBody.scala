package sttp.tapir.server.netty.internal

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import sttp.capabilities
import sttp.model.{HasHeaders, Part}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{
  ByteBufNettyResponseContent,
  ChunkedFileNettyResponseContent,
  ChunkedStreamNettyResponseContent
}
import sttp.tapir.{CodecFormat, FileRange, RawBodyType, RawPart, WebSocketBodyOutput}

import java.io.{InputStream, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.UUID

class NettyToResponseBody extends ToResponseBody[NettyResponse, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse = {
    bodyType match {
      case RawBodyType.StringBody(charset) =>
        val bytes = v.asInstanceOf[String].getBytes(charset)
        (ctx: ChannelHandlerContext) => ByteBufNettyResponseContent(ctx.newPromise(), Unpooled.wrappedBuffer(bytes))

      case RawBodyType.ByteArrayBody =>
        val bytes = v.asInstanceOf[Array[Byte]]
        (ctx: ChannelHandlerContext) => ByteBufNettyResponseContent(ctx.newPromise(), Unpooled.wrappedBuffer(bytes))

      case RawBodyType.ByteBufferBody =>
        val byteBuffer = v.asInstanceOf[ByteBuffer]
        (ctx: ChannelHandlerContext) => ByteBufNettyResponseContent(ctx.newPromise(), Unpooled.wrappedBuffer(byteBuffer))

      case RawBodyType.InputStreamBody =>
        val stream = v.asInstanceOf[InputStream]
        (ctx: ChannelHandlerContext) => ChunkedStreamNettyResponseContent(ctx.newPromise(), wrap(stream))

      case RawBodyType.FileBody =>
        val fileRange = v.asInstanceOf[FileRange]
        (ctx: ChannelHandlerContext) => ChunkedFileNettyResponseContent(ctx.newPromise(), wrap(fileRange))

      case m: RawBodyType.MultipartBody =>
        val buffers: List[ByteBuf] = v
          .asInstanceOf[List[RawPart]]
          .flatMap(part =>
            m.partType(part.name)
              .map(bodyType => convertToBuffs(bodyType, part))
          )
        (ctx: ChannelHandlerContext) => ByteBufNettyResponseContent(ctx.newPromise(), Unpooled.wrappedBuffer(buffers: _*))
    }
  }

  private def convertToBuffs(bodyType: RawBodyType[_], p: Part[Any]): ByteBuf = {
    bodyType match {
      case RawBodyType.StringBody(_) =>
        toPart(p.body, p.contentType.get, p.name, None)
      case RawBodyType.ByteArrayBody =>
        toPart(p.body, p.contentType.get, p.name, None)
      case RawBodyType.ByteBufferBody =>
        toPart(p.body, p.contentType.get, p.name, None)
      case RawBodyType.InputStreamBody =>
        toPart(p.body, p.contentType.get, p.name, None)
      case RawBodyType.FileBody =>
        val fileRange = p.body.asInstanceOf[FileRange]
        toPart(Files.readString(fileRange.file.toPath), p.contentType.get, p.name, Some(fileRange.file.getName))
      case RawBodyType.MultipartBody(_, _) =>
        throw new UnsupportedOperationException("Nested multipart messages are not supported.")
    }
  }

  private def wrap(content: InputStream): ChunkedStream = {
    new ChunkedStream(content)
  }

  private def wrap(content: FileRange): ChunkedFile = {
    val file = content.file
    val maybeRange = for {
      range <- content.range
      start <- range.start
      end <- range.end
    } yield (start, end + NettyToResponseBody.IncludingLastOffset)

    maybeRange match {
      case Some((start, end)) =>
        val randomAccessFile = new RandomAccessFile(file, NettyToResponseBody.ReadOnlyAccessMode)
        new ChunkedFile(randomAccessFile, start, end - start, NettyToResponseBody.DefaultChunkSize)
      case None => new ChunkedFile(file)
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

  private def toPart(data: Any, contentType: String, name: String, filename: Option[String]): ByteBuf = {
    val boundary = UUID.randomUUID.toString
    val fileNameStr = filename.map(c => s"filename=\"$c\";").getOrElse("")
    val textPart =
      s"""
      $boundary
          Content-Type: $contentType
          Content-Disposition: form-data; $fileNameStr name="$name"

          $data
      $boundary
      """
    Unpooled.wrappedBuffer(textPart.getBytes)
  }
}

object NettyToResponseBody {
  private val DefaultChunkSize = 8192
  private val IncludingLastOffset = 1
  private val ReadOnlyAccessMode = "r"
}
