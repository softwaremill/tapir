package sttp.tapir.server.netty.sync.internal.ws

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.websocketx.{CloseWebSocketFrame, WebSocketCloseStatus, WebSocketFrame as NettyWebSocketFrame}
import org.reactivestreams.{Processor, Subscriber, Subscription}
import org.slf4j.LoggerFactory
import ox.*
import ox.channels.ChannelClosedException
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
import ox.flow.Flow

private[sync] object OxSourceWebSocketProcessor:
  private val logger = LoggerFactory.getLogger(getClass.getName)
  private val outgoingCloseAfterCloseTimeout = 1.second

  def apply[REQ, RESP](
      oxDispatcher: OxDispatcher,
      processingPipe: OxStreams.Pipe[REQ, RESP],
      o: WebSocketBodyOutput[OxStreams.Pipe[REQ, RESP], REQ, RESP, ?, OxStreams],
      ctx: ChannelHandlerContext
  ): Processor[NettyWebSocketFrame, NettyWebSocketFrame] =
    def decodeFrame(f: WebSocketFrame): REQ = o.requests.decode(f) match {
      case failure: DecodeResult.Failure         => throw new WebSocketFrameDecodeFailure(f, failure)
      case x: DecodeResult.Value[REQ] @unchecked => x.v
    }

    val frame2FramePipe: OxStreams.Pipe[NettyWebSocketFrame, NettyWebSocketFrame] = incoming =>
      val closeSignal = new Semaphore(0)
      incoming
        .map { f =>
          val sttpFrame = nettyFrameToFrame(f)
          f.release()
          sttpFrame
        }
        .pipe(takeUntilCloseFrame(passAlongCloseFrame = o.decodeCloseRequests, closeSignal))
        .pipe(optionallyConcatenateFrames(o.concatenateFragmentedFrames))
        .map(decodeFrame)
        .pipe(processingPipe)
        .pipe(monitorOutgoingClosedAfterClientClose(closeSignal))
        .map(r => frameToNettyFrame(o.responses.encode(r)))

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
  end apply

  private def optionallyConcatenateFrames(doConcatenate: Boolean)(f: Flow[WebSocketFrame]): Flow[WebSocketFrame] =
    if doConcatenate then f.mapStateful(() => None: Accumulator)(accumulateFrameState).collect { case Some(f: WebSocketFrame) => f }
    else f

  private def takeUntilCloseFrame(passAlongCloseFrame: Boolean, closeSignal: Semaphore)(f: Flow[WebSocketFrame]): Flow[WebSocketFrame] =
    f.takeWhile(
      {
        case _: WebSocketFrame.Close => closeSignal.release(); false
        case f                       => true
      },
      includeFirstFailing = passAlongCloseFrame
    )

  private def monitorOutgoingClosedAfterClientClose[T](closeSignal: Semaphore)(outgoing: Flow[T]): Flow[T] =
    // when the client closes the connection, the outgoing flow has to be completed as well, in the client's pipeline
    // code; monitoring that this happens within a timeout after the close happens
    Flow.usingEmit { emit =>
      unsupervised {
        forkUnsupervised {
          // after the close frame is received from the client, waiting for the given grace period for the flow to
          // complete. This will end this scope, and interrupt the `sleep`. If this doesn't happen, logging an error.
          closeSignal.acquire()
          sleep(outgoingCloseAfterCloseTimeout)
          logger.error(
            s"WebSocket outgoing messages flow either not drained, or not closed, " +
              s"$outgoingCloseAfterCloseTimeout after receiving a close frame from the client! " +
              s"Make sure to complete the outgoing flow in your pipeline, once the incoming " +
              s"flow is done!"
          )
        }

        outgoing.runToEmit(emit)
      }
    }
