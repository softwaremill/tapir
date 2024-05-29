package sttp.tapir.server.netty.sync.internal.ws

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.{CloseWebSocketFrame, WebSocketCloseStatus, WebSocketFrame => NettyWebSocketFrame}
import org.reactivestreams.{Processor, Subscriber, Subscription}
import org.slf4j.LoggerFactory
import ox.*
import ox.channels.{ChannelClosedException, Source}
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.netty.internal.ws.WebSocketFrameConverters._
import sttp.tapir.server.netty.sync.OxStreams
import sttp.tapir.server.netty.sync.internal.ox.OxDispatcher
import sttp.tapir.server.netty.sync.internal.reactivestreams.OxProcessor
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame
import java.io.IOException

private[sync] object OxSourceWebSocketProcessor:

  def apply[REQ, RESP](
      oxDispatcher: OxDispatcher,
      pipe: OxStreams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams],
      ctx: ChannelHandlerContext
  ): Processor[NettyWebSocketFrame, NettyWebSocketFrame] =
    val frame2FramePipe: OxStreams.Pipe[NettyWebSocketFrame, NettyWebSocketFrame] =
      (source: Source[NettyWebSocketFrame]) => {
        pipe(
          optionallyConcatenateFrames(
            source
              .mapAsView { f =>
                val sttpFrame = nettyFrameToFrame(f)
                f.release()
                sttpFrame
              },
            o.concatenateFragmentedFrames
          )
            .mapAsView(f =>
              o.requests.decode(f) match {
                case failure: DecodeResult.Failure         => throw new WebSocketFrameDecodeFailure(f, failure)
                case x: DecodeResult.Value[REQ] @unchecked => x.v
              }
            )
        )
          .mapAsView(r => frameToNettyFrame(o.responses.encode(r)))
      }
    // We need this kind of interceptor to make Netty reply correctly to closed channel or error
    def wrapSubscriberWithNettyCallback[B](sub: Subscriber[? >: B]): Subscriber[? >: B] = new Subscriber[B] {
      private val logger = LoggerFactory.getLogger(getClass.getName)
      override def onSubscribe(s: Subscription): Unit = sub.onSubscribe(s)
      override def onNext(t: B): Unit = sub.onNext(t)
      override def onError(t: Throwable): Unit =
        t match
          case ChannelClosedException.Error(e: IOException) =>
            // Connection reset?
            logger.info("Web Socket channel closed abnormally", e)
          case e =>
            logger.error("Web Socket channel closed abnormally", e)
        val _ = ctx.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.INTERNAL_SERVER_ERROR, "Internal Server Error"))
        sub.onError(t)
      override def onComplete(): Unit =
        val _ = ctx.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, "Bye"))
        sub.onComplete()
    }
    new OxProcessor(oxDispatcher, frame2FramePipe, wrapSubscriberWithNettyCallback)

  private def optionallyConcatenateFrames(s: Source[WebSocketFrame], doConcatenate: Boolean)(using Ox): Source[WebSocketFrame] =
    if doConcatenate then
      s.mapStateful(() => None: Accumulator)(accumulateFrameState).collectAsView { case Some(f: WebSocketFrame) => f }
    else s
