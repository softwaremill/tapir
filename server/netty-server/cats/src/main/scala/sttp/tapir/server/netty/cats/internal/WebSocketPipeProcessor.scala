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
  // Not really that unsafe. Subscriber creation doesn't do any IO, only initializes an AtomicReference in an initial state.
  private val subscriber: StreamSubscriber[F, NettyWebSocketFrame] = dispatcher.unsafeRunSync(
    // If bufferSize > 1, the stream may stale and not emit responses until enough requests are buffered
    StreamSubscriber[F, NettyWebSocketFrame](bufferSize = 1)
  )
  private val publisher: Promise[Publisher[NettyWebSocketFrame]] = Promise[Publisher[NettyWebSocketFrame]]()
  private val logger = LoggerFactory.getLogger(getClass.getName)

  override def onSubscribe(s: Subscription): Unit = {
    val subscription = new NonCancelingSubscription(s)
    val in: Stream[F, NettyWebSocketFrame] = subscriber.sub.stream(Applicative[F].unit)
    val sttpFrames = in.map { f =>
      val sttpFrame = nettyFrameToFrame(f)
      f.release()
      sttpFrame
    }
    val stream: Stream[F, NettyWebSocketFrame] =
      optionallyConcatenateFrames(sttpFrames, o.concatenateFragmentedFrames)
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
            Sync[F].delay { val _ = wsCompletedPromise.setSuccess() }
          case ExitCase.Errored(t) =>
            Sync[F].delay(wsCompletedPromise.setFailure(t)) >> Sync[F].delay(logger.error("Error occured in WebSocket channel", t))
          case ExitCase.Canceled =>
            Sync[F].delay { val _ = wsCompletedPromise.cancel(true) }
        }
        .append(fs2.Stream(frameToNettyFrame(WebSocketFrame.close)))

    // Trigger listening for WS frames in the underlying fs2 StreamSubscribber
    subscriber.sub.onSubscribe(subscription)
    // Signal that a Publisher is ready to send result frames
    publisher.success(StreamUnicastPublisher(stream, dispatcher))
  }

  override def onNext(t: NettyWebSocketFrame): Unit = {
    subscriber.sub.onNext(t)
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

  override def subscribe(s: Subscriber[_ >: NettyWebSocketFrame]): Unit = {
    // A subscriber may come to read from our internal Publisher. It has to wait for the Publisher to be initialized.
    publisher.future.onComplete {
      case Success(p) =>
        p.subscribe(s)
      case _ => // Never happens, we call succecss() explicitly
    }(ExecutionContexts.sameThread)

  }

  private def optionallyConcatenateFrames(s: Stream[F, WebSocketFrame], doConcatenate: Boolean): Stream[F, WebSocketFrame] =
    if (doConcatenate) {
      type Accumulator = Option[Either[Array[Byte], String]]

      s.mapAccumulate(None: Accumulator) {
        case (None, f: WebSocketFrame.Ping)                                  => (None, Some(f))
        case (None, f: WebSocketFrame.Pong)                                  => (None, Some(f))
        case (None, f: WebSocketFrame.Close)                                 => (None, Some(f))
        case (None, f: WebSocketFrame.Data[_]) if f.finalFragment            => (None, Some(f))
        case (Some(Left(acc)), f: WebSocketFrame.Binary) if f.finalFragment  => (None, Some(f.copy(payload = acc ++ f.payload)))
        case (Some(Left(acc)), f: WebSocketFrame.Binary) if !f.finalFragment => (Some(Left(acc ++ f.payload)), None)
        case (Some(Right(acc)), f: WebSocketFrame.Text) if f.finalFragment   => (None, Some(f.copy(payload = acc + f.payload)))
        case (Some(Right(acc)), f: WebSocketFrame.Text) if !f.finalFragment  => (Some(Right(acc + f.payload)), None)
        case (acc, f) => throw new IllegalStateException(s"Cannot accumulate web socket frames. Accumulator: $acc, frame: $f.")
      }.collect { case (_, Some(f)) => f }
    } else s
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
