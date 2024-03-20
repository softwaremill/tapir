package sttp.tapir.server.netty.cats.internal

import cats.Applicative
import cats.effect.kernel.Async
import cats.effect.std.Dispatcher
import fs2.interop.reactivestreams.{StreamSubscriber, StreamUnicastPublisher}
import fs2.{Pipe, Stream}
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => NettyWebSocketFrame}
import org.reactivestreams.{Processor, Publisher, Subscriber, Subscription}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.server.netty.internal.WebSocketFrameConverters._
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Promise
import scala.util.{Failure, Success}

class WebSocketPipeProcessor[F[_]: Async, REQ, RESP](
    pipe: Pipe[F, REQ, RESP],
    dispatcher: Dispatcher[F],
    o: WebSocketBodyOutput[Pipe[F, REQ, RESP], REQ, RESP, ?, Fs2Streams[F]]
) extends Processor[NettyWebSocketFrame, NettyWebSocketFrame] {
  private var subscriber: StreamSubscriber[F, NettyWebSocketFrame] = _
  private val publisher: Promise[Publisher[NettyWebSocketFrame]] = Promise[Publisher[NettyWebSocketFrame]]()
  private var subscription: Subscription = _

  override def onSubscribe(s: Subscription): Unit = {
    subscriber = dispatcher.unsafeRunSync(
      StreamSubscriber[F, NettyWebSocketFrame](bufferSize = 1)
    )
    subscription = s
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
        .append(fs2.Stream(frameToNettyFrame(WebSocketFrame.close)))

    subscriber.sub.onSubscribe(s)
    publisher.success(StreamUnicastPublisher(stream, dispatcher))
  }

  override def onNext(t: NettyWebSocketFrame): Unit = {
    subscriber.sub.onNext(t)
  }

  override def onError(t: Throwable): Unit = {
    subscriber.sub.onError(t)
  }

  override def onComplete(): Unit = {
    subscriber.sub.onComplete()
  }

  override def subscribe(s: Subscriber[_ >: NettyWebSocketFrame]): Unit = {
    publisher.future.onComplete {
      case Success(p) =>
        p.subscribe(s)
      case Failure(ex) =>
        subscriber.sub.onError(ex)
        subscription.cancel
    }(Implicits.global)
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
