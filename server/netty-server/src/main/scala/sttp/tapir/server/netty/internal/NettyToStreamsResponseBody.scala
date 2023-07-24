package sttp.tapir.server.netty.internal

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.stream.{ChunkedFile, ChunkedStream}
import sttp.capabilities
import sttp.capabilities.Streams
import sttp.model.HasHeaders
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{
  ByteBufNettyResponseContent,
  ChunkedFileNettyResponseContent,
  ChunkedStreamNettyResponseContent,
  ReactivePublisherNettyResponseContent
}
import sttp.tapir.{CodecFormat, FileRange, InputStreamRange, RawBodyType, WebSocketBodyOutput}

import java.io.{InputStream, RandomAccessFile}
import java.nio.ByteBuffer
import java.nio.charset.Charset

class NettyToStreamsResponseBody[S <: Streams[S]](delegate: NettyToResponseBody, streamCompatible: StreamCompatible[S])
    extends ToResponseBody[NettyResponse, S] {

  override val streams: S = streamCompatible.streams

  override def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse = {
    bodyType match {
      case RawBodyType.InputStreamBody =>
        (ctx: ChannelHandlerContext) =>
          new ReactivePublisherNettyResponseContent(ctx.newPromise(), streamCompatible.publisherFromInputStream(() => v, length = None))

      case RawBodyType.InputStreamRangeBody =>
        (ctx: ChannelHandlerContext) =>
          new ReactivePublisherNettyResponseContent(
            ctx.newPromise(),
            streamCompatible.publisherFromInputStream(v.inputStreamFromRangeStart, length = v.range.map(_.contentLength))
          )

      case RawBodyType.FileBody =>
        (ctx: ChannelHandlerContext) => new ReactivePublisherNettyResponseContent(ctx.newPromise(), streamCompatible.publisherFromFile(v))

      case _: RawBodyType.MultipartBody => throw new UnsupportedOperationException

      case _ => delegate.fromRawValue(v, headers, format, bodyType)
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
