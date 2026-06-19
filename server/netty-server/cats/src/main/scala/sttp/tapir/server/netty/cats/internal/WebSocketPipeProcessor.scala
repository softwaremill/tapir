package sttp.tapir.server.netty.cats.internal

import cats.Applicative
import cats.effect.kernel.Resource.ExitCase
import cats.effect.kernel.{Async, Sync}
import cats.effect.std.Dispatcher
import cats.syntax.all._
import fs2.interop.reactivestreams.{StreamSubscriber, StreamUnicastPublisher}
import fs2.{Pipe, Stream}
import io.netty.channel.ChannelPromise
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => NettyWebSocketFrame}
import org.reactivestreams.{Processor, Publisher, Subscriber, Subscription}
import org.slf4j.LoggerFactory
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.netty.internal.ws.WebSocketFrameConverters._
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

import scala.concurrent.Promise
import scala.util.Success

/** A Reactive Streams Processor[NettyWebSocketFrame, NettyWebSocketFrame] built from a fs2.Pipe[F, REQ, RESP] passed from an WS endpoint.
  */
class WebSocketPipeProcessor[F[_]: Async, REQ, RESP](
    pipe: Pipe[F, REQ, RESP],
    dispatcher: Dispatcher[F],
    o: WebSocketBodyOutput[Pipe[F, REQ, RESP], REQ, RESP, ?, Fs2Streams[F]],
    wsCompletedPromise: ChannelPromise
) extends Processor[NettyWebSocketFrame, NettyWebSocketFrame] {
  // Holds already-converted sttp frames (not netty frames): each incoming netty frame is converted and released in
  // `onNext` (at the boundary), so a frame buffered here is never an un-released ByteBuf.
  // Not really that unsafe. Subscriber creation doesn't do any IO, only initializes an AtomicReference in an initial state.
  private val subscriber: StreamSubscriber[F, WebSocketFrame] = dispatcher.unsafeRunSync(
    // If bufferSize > 1, the stream may stale and not emit responses until enough requests are buffered
    StreamSubscriber[F, WebSocketFrame](bufferSize = 1)
  )
  private val publisher: Promise[Publisher[NettyWebSocketFrame]] = Promise[Publisher[NettyWebSocketFrame]]()
  private val logger = LoggerFactory.getLogger(getClass.getName)

  // The upstream (netty) subscription, kept so that once the WS interaction has completed we can keep requesting inbound
  // frames and release them in `onNext`. Otherwise late frames (e.g. the client's Close in response to ours) would sit
  // unreleased in the netty-reactive-streams publisher's buffer, which only releases its buffer on cancel - and we
  // intentionally don't cancel, as that would close the channel before error/close handling completes.
  @volatile private var inboundSubscription: Subscription = _

  private def drainInbound(): Unit =
    if (inboundSubscription != null) inboundSubscription.request(Long.MaxValue)

  override def onSubscribe(s: Subscription): Unit = {
    inboundSubscription = s
    val subscription = new NonCancelingSubscription(s)
    // frames are already converted to sttp frames (and the netty frames released) in `onNext`
    val sttpFrames: Stream[F, WebSocketFrame] = subscriber.sub.stream(Applicative[F].unit)
    val stream: Stream[F, NettyWebSocketFrame] =
      optionallyConcatenateFrames(o.concatenateFragmentedFrames)(
        takeUntilCloseFrame(passAlongCloseFrame = o.decodeCloseRequests)(sttpFrames)
      )
        .map(f =>
          o.requests.decode(f) match {
            case x: DecodeResult.Value[REQ]    => x.v
            case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
          }
        )
        .through(pipe)
        .map(r => frameToNettyFrame(o.responses.encode(r)))
        .onFinalizeCaseWeak {
          case ExitCase.Succeeded =>
            Sync[F].delay {
              // Send the closing frame by writing it directly to the channel, rather than appending it to the outgoing
              // stream. Netty releases the frame as part of the write - even if the channel is already closed - while a
              // frame emitted through the reactive-streams bridge during teardown can be dropped without being released
              // (the channel may already be closing once the stream completes). Writing it before completing the promise
              // also ensures it's enqueued before any channel close triggered by the promise's listeners.
              wsCompletedPromise.channel().writeAndFlush(frameToNettyFrame(WebSocketFrame.close)): Unit
              val _ = wsCompletedPromise.setSuccess()
              // keep draining (and releasing, in onNext) any inbound frames that arrive during teardown
              drainInbound()
            }
          case ExitCase.Errored(t) =>
            Sync[F].delay {
              val _ = wsCompletedPromise.setFailure(t)
              logger.error("Error occured in WebSocket channel", t)
              drainInbound()
            }
          case ExitCase.Canceled =>
            Sync[F].delay {
              val _ = wsCompletedPromise.cancel(true)
              drainInbound()
            }
        }

    // Trigger listening for WS frames in the underlying fs2 StreamSubscribber
    subscriber.sub.onSubscribe(subscription)
    // Signal that a Publisher is ready to send result frames
    publisher.success(StreamUnicastPublisher(stream, dispatcher))
  }

  override def onNext(t: NettyWebSocketFrame): Unit = {
    // Convert (copying the payload) and release the netty frame here, at the boundary, so the inbound ByteBuf is always
    // released. Once the WS interaction has completed the stream no longer consumes frames, so we don't forward them -
    // but we still drain (see drainInbound) and release them here, so they don't leak in the publisher's buffer.
    val sttpFrame =
      try nettyFrameToFrame(t)
      finally { val _ = t.release() }
    if (!wsCompletedPromise.isDone()) subscriber.sub.onNext(sttpFrame)
  }

  override def onError(t: Throwable): Unit = {
    subscriber.sub.onError(t)
    if (!wsCompletedPromise.isDone()) {
      val _ = wsCompletedPromise.setFailure(t)
    }
  }

  override def onComplete(): Unit = {
    subscriber.sub.onComplete()
    if (!wsCompletedPromise.isDone()) {
      val _ = wsCompletedPromise.setSuccess()
    }
  }

  override def subscribe(s: Subscriber[? >: NettyWebSocketFrame]): Unit = {
    // A subscriber may come to read from our internal Publisher. It has to wait for the Publisher to be initialized.
    publisher.future.onComplete {
      case Success(p) =>
        p.subscribe(s)
      case _ => // Never happens, we call succecss() explicitly
    }(ExecutionContexts.sameThread)

  }

  private def optionallyConcatenateFrames(doConcatenate: Boolean)(s: Stream[F, WebSocketFrame]): Stream[F, WebSocketFrame] =
    if (doConcatenate) {
      s.mapAccumulate(None: Accumulator)(accumulateFrameState).collect { case (_, Some(f)) => f }
    } else s

  private def takeUntilCloseFrame(passAlongCloseFrame: Boolean)(s: Stream[F, WebSocketFrame]): Stream[F, WebSocketFrame] =
    s.takeWhile(
      {
        case _: WebSocketFrame.Close => false
        case _                       => true
      },
      takeFailure = passAlongCloseFrame
    )
}

/** A special wrapper used to override internal logic of fs2, which calls cancel() silently when internal stream failures happen, causing
  * the subscription to close the channel and stop the subscriber in such a way that errors can't get handled properly. With this wrapper we
  * intentionally don't do anything on cancel(), so that the stream continues to fail properly on errors. We are handling cancelation
  * manually with a channel promise passed to the processor logic.
  * @param delegate
  *   a channel subscription which we don't want to notify about cancelation.
  */
class NonCancelingSubscription(delegate: Subscription) extends Subscription {
  override def cancel(): Unit = ()
  override def request(n: Long): Unit = {
    delegate.request(n)
  }
}
