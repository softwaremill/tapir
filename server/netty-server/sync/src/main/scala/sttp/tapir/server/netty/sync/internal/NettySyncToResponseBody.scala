package sttp.tapir.server.netty.sync.internal

import _root_.ox.flow.Flow
import _root_.ox.flow.reactive.toReactiveStreamsPublisher
import _root_.ox.{Chunk, InScopeRunner, forkDiscard}
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Publisher, Subscriber}
import sttp.model.HasHeaders
import sttp.tapir.*
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{ReactivePublisherNettyResponseContent, ReactiveWebSocketProcessorNettyResponseContent}
import sttp.tapir.server.netty.internal.NettyToResponseBodyBase
import sttp.tapir.server.netty.sync.internal.reactivestreams.InputStreamSyncPublisher
import sttp.tapir.server.netty.sync.*
import sttp.tapir.server.netty.internal.NettyToResponseBodyCommon.DefaultChunkSize

import java.nio.charset.Charset

private[sync] class NettySyncToResponseBody(inScopeRunner: InScopeRunner)
    extends NettyToResponseBodyBase[OxStreams]:

  override val streams: OxStreams = OxStreams

  protected def wrap(streamRange: InputStreamRange): Publisher[HttpContent] = {
    new InputStreamSyncPublisher(streamRange, DefaultChunkSize)
  }


  def fromStreamValue(v: Flow[Chunk[Byte]], headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): NettyResponse =
    (ctx: ChannelHandlerContext) =>
      new ReactivePublisherNettyResponseContent(
        ctx.newPromise(),
        // we can only create a publisher from `v` within a concurrency scope; using the main concurrency scope for
        // that, via `externalRunner`. As we need to return a `Publisher` immediately, deferring the flow-to-publisher
        // transformation until the subscriber is known.
        new Publisher[HttpContent]:
          override def subscribe(s: Subscriber[_ >: HttpContent]): Unit =
            inScopeRunner.async(
              forkDiscard(
                v.map(chunk => new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray)))
                  .toReactiveStreamsPublisher
                  .subscribe(s)
              )
            )
      )

  override def fromWebSocketPipe[REQ, RESP](
      pipe: streams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[streams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams]
  ): NettyResponse = (ctx: ChannelHandlerContext) =>
    val channelPromise = ctx.newPromise()
    new ReactiveWebSocketProcessorNettyResponseContent(
      channelPromise,
      ws.OxSourceWebSocketProcessor[REQ, RESP](
        inScopeRunner,
        pipe.asInstanceOf[OxStreams.Pipe[REQ, RESP]],
        o.asInstanceOf[WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams]],
        ctx
      ),
      ignorePong = o.ignorePong,
      autoPongOnPing = o.autoPongOnPing,
      decodeCloseRequests = o.decodeCloseRequests,
      autoPing = o.autoPing
    )

