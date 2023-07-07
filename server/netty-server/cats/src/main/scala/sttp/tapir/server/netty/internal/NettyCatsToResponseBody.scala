package sttp.tapir.server.netty.internal

import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import fs2.interop.reactivestreams._
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{
  ByteBufNettyResponseContent,
  ChunkedFileNettyResponseContent,
  ChunkedStreamNettyResponseContent
}
import sttp.tapir.{CodecFormat, FileRange, InputStreamRange, RawBodyType, WebSocketBodyOutput}

import java.io.{InputStream, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent
import org.reactivestreams.Publisher
import io.netty.buffer.ByteBuf
import org.reactivestreams.Subscriber
import fs2.Chunk
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.DefaultHttpContent

private[netty] class RangedChunkedStream(raw: InputStream, length: Long) extends ChunkedStream(raw) {

  override def isEndOfInput(): Boolean =
    super.isEndOfInput || transferredBytes == length
}

class NettyCatsToResponseBody[F[_]: Async](dispatcher: Dispatcher[F]) extends ToResponseBody[NettyResponse, Fs2Streams[F]] {
  override val streams: Fs2Streams[F] = Fs2Streams[F]

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
        (ctx: ChannelHandlerContext) => ChunkedStreamNettyResponseContent(ctx.newPromise(), wrap(v))

      case RawBodyType.InputStreamRangeBody =>
        (ctx: ChannelHandlerContext) => ChunkedStreamNettyResponseContent(ctx.newPromise(), wrap(v))

      case RawBodyType.FileBody =>
        (ctx: ChannelHandlerContext) => ChunkedFileNettyResponseContent(ctx.newPromise(), wrap(v))

      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException
    }
  }

  private def wrap(streamRange: InputStreamRange): ChunkedStream = {
    streamRange.range
      .map(r => new RangedChunkedStream(streamRange.inputStreamFromRangeStart(), r.contentLength))
      .getOrElse(new ChunkedStream(streamRange.inputStream()))
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
      case Some((start, end)) => {
        val randomAccessFile = new RandomAccessFile(file, NettyToResponseBody.ReadOnlyAccessMode)
        new ChunkedFile(randomAccessFile, start, end - start, NettyToResponseBody.DefaultChunkSize)
      }
      case None => new ChunkedFile(file)
    }
  }

  def fs2StreamToPublisher(stream: streams.BinaryStream): Publisher[HttpContent] = {
    // Deprecated constructor, but the proposed one does roughly the same, forcing a dedicated
    // dispatcher, which results in a Resource[], which is hard to afford here
    StreamUnicastPublisher(
      stream.chunks
        .map { chunk =>
          val bytes: Chunk.ArraySlice[Byte] = chunk.compact
          new DefaultHttpContent(Unpooled.wrappedBuffer(bytes.values, bytes.offset, bytes.length))
        },
      dispatcher
    )
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): NettyResponse =
    (ctx: ChannelHandlerContext) => {
      new NettyResponseContent.ReactivePublisherNettyResponseContent(
        ctx.newPromise(), 
        fs2StreamToPublisher(v))
    }

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
  ): NettyResponse = throw new UnsupportedOperationException
}
