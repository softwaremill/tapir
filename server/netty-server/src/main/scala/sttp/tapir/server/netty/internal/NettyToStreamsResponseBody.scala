package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import sttp.capabilities.Streams
import sttp.model.HasHeaders
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.internal.NettyToResponseBody._
import sttp.tapir.server.netty.NettyResponseContent.{ByteBufNettyResponseContent, ReactivePublisherNettyResponseContent}
import sttp.tapir.{CodecFormat, RawBodyType, WebSocketBodyOutput}

import java.nio.ByteBuffer
import java.nio.charset.Charset

/** Common logic for producing response body in all Netty backends that support streaming. These backends use streaming libraries like fs2
  * or zio-streams to obtain reactive Publishers representing responses like InputStreamBody, InputStreamRangeBody or FileBody. Other kinds
  * of raw responses like directly available String, ByteArray or ByteBuffer can be returned without wrapping into a Publisher.
  */
private[netty] class NettyToStreamsResponseBody[S <: Streams[S]](streamCompatible: StreamCompatible[S])
    extends ToResponseBody[NettyResponse, S] {

  override val streams: S = streamCompatible.streams

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
        (ctx: ChannelHandlerContext) =>
          new ReactivePublisherNettyResponseContent(
            ctx.newPromise(),
            streamCompatible.publisherFromInputStream(() => v, DefaultChunkSize, length = None)
          )

      case RawBodyType.InputStreamRangeBody =>
        (ctx: ChannelHandlerContext) =>
          new ReactivePublisherNettyResponseContent(
            ctx.newPromise(),
            streamCompatible.publisherFromInputStream(v.inputStreamFromRangeStart, DefaultChunkSize, length = v.range.map(_.contentLength))
          )

      case RawBodyType.FileBody =>
        (ctx: ChannelHandlerContext) =>
          new ReactivePublisherNettyResponseContent(ctx.newPromise(), streamCompatible.publisherFromFile(v, DefaultChunkSize))

      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException
    }
  }

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): NettyResponse = (ctx: ChannelHandlerContext) => {
    new ReactivePublisherNettyResponseContent(
      ctx.newPromise(),
      streamCompatible.asPublisher(v.asInstanceOf[streamCompatible.streams.BinaryStream])
    )
  }

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]
  ): NettyResponse = throw new UnsupportedOperationException
}
