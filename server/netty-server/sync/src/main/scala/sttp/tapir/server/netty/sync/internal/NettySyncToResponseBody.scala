package sttp.tapir.server.netty.sync.internal

import _root_.ox.flow.reactive.toReactiveStreamsPublisher
import _root_.ox.{ExternalRunner, forkDiscard}
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Publisher, Subscriber}
import sttp.model.HasHeaders
import sttp.monad.MonadError
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.interpreter.ToResponseBody
import sttp.tapir.server.netty.NettyResponse
import sttp.tapir.server.netty.NettyResponseContent.{ReactivePublisherNettyResponseContent, ReactiveWebSocketProcessorNettyResponseContent}
import sttp.tapir.server.netty.internal.{NettyToResponseBody, RunAsync}
import sttp.tapir.server.netty.sync.*
import sttp.tapir.server.netty.sync.internal.ox.OxDispatcher

import java.nio.charset.Charset
import _root_.ox.flow.Flow
import _root_.ox.Chunk

private[sync] class NettySyncToResponseBody(runAsync: RunAsync[Identity], oxDispatcher: OxDispatcher, externalRunner: ExternalRunner)(using
    me: MonadError[Identity]
) extends ToResponseBody[NettyResponse, OxStreams]:

  val delegate = new NettyToResponseBody(runAsync)(me)

  override val streams: OxStreams = OxStreams

  def fromRawValue[R](v: R, headers: HasHeaders, format: CodecFormat, bodyType: RawBodyType[R]): NettyResponse =
    delegate.fromRawValue(v, headers, format, bodyType)

  def fromStreamValue(v: Flow[Chunk[Byte]], headers: HasHeaders, format: CodecFormat, charset: Option[Charset]): NettyResponse =
    (ctx: ChannelHandlerContext) =>
      new ReactivePublisherNettyResponseContent(
        ctx.newPromise(),
        // we can only create a publisher from `v` within a concurrency scope; using the main concurrency scope for
        // that, via `externalRunner`. As we need to return a `Publisher` immediately, deferring the flow-to-publisher
        // transformation until the subscriber is known.
        new Publisher[HttpContent]:
          override def subscribe(s: Subscriber[_ >: HttpContent]): Unit =
            externalRunner.runAsync(
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
        oxDispatcher,
        pipe.asInstanceOf[OxStreams.Pipe[REQ, RESP]],
        o.asInstanceOf[WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams]],
        ctx
      ),
      ignorePong = o.ignorePong,
      autoPongOnPing = o.autoPongOnPing,
      decodeCloseRequests = o.decodeCloseRequests,
      autoPing = o.autoPing
    )
