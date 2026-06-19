package sttp.tapir.server.netty.internal

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.Streams
import sttp.model.HasHeaders
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{ReactivePublisherNettyResponseContent, ReactiveWebSocketProcessorNettyResponseContent}
import sttp.tapir.server.netty.internal.NettyToResponseBody._
import sttp.tapir.{CodecFormat, FileRange, InputStreamRange, WebSocketBodyOutput}

import java.io.InputStream
import java.nio.charset.Charset

/** Common logic for producing response body in all Netty backends that support streaming. These backends use streaming libraries like fs2
  * or zio-streams to obtain reactive Publishers representing responses like InputStreamBody, InputStreamRangeBody or FileBody. Other kinds
  * of raw responses like directly available String, ByteArray or ByteBuffer can be returned without wrapping into a Publisher.
  */
private[netty] class NettyToStreamsResponseBody[S <: Streams[S]](streamCompatible: StreamCompatible[S])
    extends NettyToResponseBodyCommon[S] {

  override val streams: S = streamCompatible.streams

  override def fromStreamValue(
      v: streams.BinaryStream,
      headers: HasHeaders,
      format: CodecFormat,
      charset: Option[Charset]
  ): NettyResponse = (ctx: ChannelHandlerContext) => {
    ReactivePublisherNettyResponseContent(
      ctx.newPromise(),
      streamCompatible.asPublisher(v.asInstanceOf[streamCompatible.streams.BinaryStream])
    )
  }

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, _, S]
  ): NettyResponse = (ctx: ChannelHandlerContext) => {
    ReactiveWebSocketProcessorNettyResponseContent(
      ctx.newPromise(),
      streamCompatible.asWsProcessor(
        pipe.asInstanceOf[streamCompatible.streams.Pipe[REQ, RESP]],
        o.asInstanceOf[WebSocketBodyOutput[streamCompatible.streams.Pipe[REQ, RESP], REQ, RESP, _, S]],
        ctx
      ),
      ignorePong = o.ignorePong,
      autoPongOnPing = o.autoPongOnPing,
      decodeCloseRequests = o.decodeCloseRequests,
      autoPing = o.autoPing
    )
  }

  override protected def wrap(v: InputStreamRange): Publisher[HttpContent] =
    streamCompatible.publisherFromInputStream(v.inputStreamFromRangeStart, DefaultChunkSize, length = v.range.map(_.contentLength))

  override protected def wrap(v: FileRange): Publisher[HttpContent] =
    streamCompatible.publisherFromFile(v, DefaultChunkSize)

  override protected def wrap(v: InputStream): Publisher[HttpContent] =
    streamCompatible.publisherFromInputStream(() => v, DefaultChunkSize, length = None)
}
