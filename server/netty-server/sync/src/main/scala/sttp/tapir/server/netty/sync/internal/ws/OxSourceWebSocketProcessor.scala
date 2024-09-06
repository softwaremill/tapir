package sttp.tapir.server.netty.sync.internal.ws

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.{CloseWebSocketFrame, WebSocketCloseStatus, WebSocketFrame as NettyWebSocketFrame}
import org.reactivestreams.{Processor, Subscriber, Subscription}
import org.slf4j.LoggerFactory
import ox.*
import ox.channels.{ChannelClosedException, Source}
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.netty.internal.ws.WebSocketFrameConverters.*
import sttp.tapir.server.netty.sync.OxStreams
import sttp.tapir.server.netty.sync.internal.ox.OxDispatcher
import sttp.tapir.server.netty.sync.internal.reactivestreams.OxProcessor
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

import java.io.IOException
import java.util.concurrent.Semaphore

import scala.concurrent.duration.*

private[sync] object OxSourceWebSocketProcessor:
  private val logger = LoggerFactory.getLogger(getClass.getName)
  private val outgoingCloseAfterCloseTimeout = 1.second

  def apply[REQ, RESP](
      oxDispatcher: OxDispatcher,
      pipe: OxStreams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams],
      ctx: ChannelHandlerContext
  ): Processor[NettyWebSocketFrame, NettyWebSocketFrame] =
    val frame2FramePipe: OxStreams.Pipe[NettyWebSocketFrame, NettyWebSocketFrame] = ox ?=>
      val closeSignal = new Semaphore(0)
      (incoming: Source[NettyWebSocketFrame]) =>
        val outgoing = pipe(
          optionallyConcatenateFrames(o.concatenateFragmentedFrames)(
            takeUntilCloseFrame(passAlongCloseFrame = o.decodeCloseRequests, closeSignal)(
              incoming
                .mapAsView { f =>
                  val sttpFrame = nettyFrameToFrame(f)
                  f.release()
                  sttpFrame
                }
            )
          )
            .mapAsView(f =>
              o.requests.decode(f) match {
                case failure: DecodeResult.Failure         => throw new WebSocketFrameDecodeFailure(f, failure)
                case x: DecodeResult.Value[REQ] @unchecked => x.v
              }
            )
        )
          .mapAsView(r => frameToNettyFrame(o.responses.encode(r)))

        // when the client closes the connection, we need to close the outgoing channel as well - this needs to be
        // done in the client's pipeline code; monitoring that this happens within a timeout after the close happens
        monitorOutgoingClosedAfterClientClose(closeSignal, outgoing)

        outgoing

    // We need this kind of interceptor to make Netty reply correctly to closed channel or error
    def wrapSubscriberWithNettyCallback[B](sub: Subscriber[? >: B]): Subscriber[? >: B] = new Subscriber[B] {
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
        val _ = ctx.writeAndFlush(new CloseWebSocketFrame(WebSocketCloseStatus.NORMAL_CLOSURE, "normal closure"))
        sub.onComplete()
    }
    new OxProcessor(oxDispatcher, frame2FramePipe, wrapSubscriberWithNettyCallback)

  private def optionallyConcatenateFrames(doConcatenate: Boolean)(s: Source[WebSocketFrame])(using Ox): Source[WebSocketFrame] =
    if doConcatenate then s.mapStateful(() => None: Accumulator)(accumulateFrameState).collectAsView { case Some(f: WebSocketFrame) => f }
    else s

  private def takeUntilCloseFrame(passAlongCloseFrame: Boolean, closeSignal: Semaphore)(
      s: Source[WebSocketFrame]
  )(using Ox): Source[WebSocketFrame] =
    s.takeWhile(
      {
        case _: WebSocketFrame.Close => closeSignal.release(); false
        case _                       => true
      },
      includeFirstFailing = passAlongCloseFrame
    )

  private def monitorOutgoingClosedAfterClientClose(closeSignal: Semaphore, outgoing: Source[_])(using Ox): Unit =
    // will be interrupted when outgoing is completed
    fork {
      closeSignal.acquire()
      sleep(outgoingCloseAfterCloseTimeout)
      if !outgoing.isClosedForReceive then
        logger.error(
          s"WebSocket outgoing messages channel either not drained, or not closed, " +
            s"$outgoingCloseAfterCloseTimeout after receiving a close frame from the client! " +
            s"Make sure to complete the outgoing channel in your pipeline, once the incoming " +
            s"channel is done!"
        )
    }.discard
