package sttp.tapir.server.http4s

import cats.Monad
import cats.effect.Temporal
import cats.effect.std.Queue
import cats.syntax.all._
import fs2._
import org.http4s.websocket.{WebSocketFrame => Http4sWebSocketFrame}
import scodec.bits.ByteVector
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

private[http4s] object Http4sWebSockets {
  def pipeToBody[F[_]: Temporal, REQ, RESP](
      pipe: Pipe[F, REQ, RESP],
      o: WebSocketBodyOutput[Pipe[F, REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
  ): F[Pipe[F, Http4sWebSocketFrame, Http4sWebSocketFrame]] = {
    Queue.bounded[F, WebSocketFrame](2).map { pongs => (in: Stream[F, Http4sWebSocketFrame]) =>
      val sttpFrames = in.map(http4sFrameToFrame)
      val concatenated = optionallyConcatenateFrames(sttpFrames, o.concatenateFragmentedFrames)
      val ignorePongs = optionallyIgnorePong(concatenated, o.ignorePong)
      val autoPongs = optionallyAutoPong(ignorePongs, pongs, o.autoPongOnPing)
      val autoPings = o.autoPing match {
        case Some((interval, frame)) => Stream.awakeEvery[F](interval).map(_ => frame)
        case None                    => Stream.empty
      }
      val decodeClose = optionallyDecodeClose(autoPongs, o.decodeCloseRequests)

      (decodeClose
        .map { f =>
          o.requests.decode(f) match {
            case x: DecodeResult.Value[REQ]    => x.v
            case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
          }
        }
        .through(pipe)
        .map(o.responses.encode)
        .mergeHaltL(Stream.repeatEval(pongs.take))
        .mergeHaltL(autoPings) ++ Stream(WebSocketFrame.close))
        .map(frameToHttp4sFrame)
    }
  }

  private def http4sFrameToFrame(f: Http4sWebSocketFrame): WebSocketFrame =
    f match {
      case t: Http4sWebSocketFrame.Text  => WebSocketFrame.Text(t.str, t.last, None)
      case x: Http4sWebSocketFrame.Ping  => WebSocketFrame.Ping(x.data.toArray)
      case x: Http4sWebSocketFrame.Pong  => WebSocketFrame.Pong(x.data.toArray)
      case c: Http4sWebSocketFrame.Close => WebSocketFrame.Close(c.closeCode, "")
      case _                             => WebSocketFrame.Binary(f.data.toArray, f.last, None)
    }

  private def frameToHttp4sFrame(w: WebSocketFrame): Http4sWebSocketFrame =
    w match {
      case x: WebSocketFrame.Text   => Http4sWebSocketFrame.Text(x.payload, x.finalFragment)
      case x: WebSocketFrame.Binary => Http4sWebSocketFrame.Binary(ByteVector(x.payload), x.finalFragment)
      case x: WebSocketFrame.Ping   => Http4sWebSocketFrame.Ping(ByteVector(x.payload))
      case x: WebSocketFrame.Pong   => Http4sWebSocketFrame.Pong(ByteVector(x.payload))
      case x: WebSocketFrame.Close  => Http4sWebSocketFrame.Close(x.statusCode, x.reasonText).fold(throw _, identity)
    }

  private def optionallyConcatenateFrames[F[_]](s: Stream[F, WebSocketFrame], doConcatenate: Boolean): Stream[F, WebSocketFrame] = {
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
    } else {
      s
    }
  }

  private def optionallyIgnorePong[F[_]](s: Stream[F, WebSocketFrame], doIgnore: Boolean): Stream[F, WebSocketFrame] = {
    if (doIgnore) {
      s.filter {
        case _: WebSocketFrame.Pong => false
        case _                      => true
      }
    } else s
  }

  private def optionallyAutoPong[F[_]: Monad](
      s: Stream[F, WebSocketFrame],
      pongs: Queue[F, WebSocketFrame],
      doAuto: Boolean
  ): Stream[F, WebSocketFrame] = {
    if (doAuto) {
      val trueF = true.pure[F]
      s.evalFilter {
        case ping: WebSocketFrame.Ping => pongs.offer(WebSocketFrame.Pong(ping.payload)).map(_ => false)
        case _                         => trueF
      }
    } else s
  }

  private def optionallyDecodeClose[F[_]](s: Stream[F, WebSocketFrame], doDecodeClose: Boolean): Stream[F, WebSocketFrame] =
    if (!doDecodeClose) {
      s.takeWhile {
        case _: WebSocketFrame.Close => false
        case _                       => true
      }
    } else s
}
