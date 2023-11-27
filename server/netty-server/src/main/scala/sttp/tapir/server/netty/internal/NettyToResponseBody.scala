package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{ByteBufNettyResponseContent, ReactivePublisherNettyResponseContent}
import sttp.tapir.server.netty.internal.NettyToResponseBody.DefaultChunkSize
import sttp.tapir.server.netty.internal.reactivestreams.{FileRangePublisher, InputStreamPublisher}
import sttp.tapir.{CodecFormat, FileRange, InputStreamRange, RawBodyType, WebSocketBodyOutput}

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.ForkJoinPool
import scala.concurrent.ExecutionContext

class NettyToResponseBody extends ToResponseBody[NettyResponse, NoStreams] {
  override val streams: capabilities.Streams[NoStreams] = NoStreams
  // TODO cleanup
  lazy val blockingEc: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool)

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
        (ctx: ChannelHandlerContext) => ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(v))

      case RawBodyType.InputStreamRangeBody =>
        (ctx: ChannelHandlerContext) => ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(v))

      case RawBodyType.FileBody => {
        (ctx: ChannelHandlerContext) => ReactivePublisherNettyResponseContent(ctx.newPromise(), wrap(v))

      }

      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException
    }
  }

  private def wrap(streamRange: InputStreamRange): Publisher[HttpContent] = {
    new InputStreamPublisher(streamRange, DefaultChunkSize, blockingEc)
  }

  private def wrap(fileRange: FileRange): Publisher[HttpContent] = {
    new FileRangePublisher(fileRange, DefaultChunkSize)
  }

  private def wrap(content: InputStream): Publisher[HttpContent] = {
    wrap(InputStreamRange(() => content, range = None))
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

private[internal] object NettyToResponseBody {
  val DefaultChunkSize = 8192
  val IncludingLastOffset = 1
  val ReadOnlyAccessMode = "r"
}
