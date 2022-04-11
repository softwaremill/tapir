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
    Queue.bounded[F, WebSocketFrame](1).map { pongs => (in: Stream[F, Http4sWebSocketFrame]) =>
      val sttpFrames = in.map(http4sFrameToFrame)
      val concatenated = optionallyConcatenateFrames(sttpFrames, o.concatenateFragmentedFrames)
      val ignorePongs = optionallyIgnorePong(concatenated, o.ignorePong)
      val autoPongs = optionallyAutoPong(ignorePongs, pongs, o.autoPongOnPing)
      val autoPings = o.autoPing match {
        case Some((interval, frame)) => Stream.awakeEvery[F](interval).map(_ => frame)
        case None                    => Stream.empty
      }

      (autoPongs
        .map {
          case _: WebSocketFrame.Close if !o.decodeCloseRequests => None
          case f =>
            o.requests.decode(f) match {
              case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
              case DecodeResult.Value(v)         => Some(v)
            }
        }
        .unNoneTerminate
        .through(pipe)
        .map(o.responses.encode)
        .mergeHaltL(Stream.repeatEval(pongs.take))
        .mergeHaltL(autoPings) ++ Stream(WebSocketFrame.close))
        .map(frameToHttp4sFrame)
    }
  }

  private def http4sFrameToFrame(f: Http4sWebSocketFrame): WebSocketFrame =
    f match {
      case t: Http4sWebSocketFrame.Text    => WebSocketFrame.Text(t.str, t.last, None)
      case Http4sWebSocketFrame.Ping(data) => WebSocketFrame.Ping(data.toArray)
      case Http4sWebSocketFrame.Pong(data) => WebSocketFrame.Pong(data.toArray)
      case c: Http4sWebSocketFrame.Close   => WebSocketFrame.Close(c.closeCode, "")
      case _                               => WebSocketFrame.Binary(f.data.toArray, f.last, None)
    }

  private def frameToHttp4sFrame(w: WebSocketFrame): Http4sWebSocketFrame = {
    w match {
      case WebSocketFrame.Text(p, finalFragment, _)   => Http4sWebSocketFrame.Text(p, finalFragment)
      case WebSocketFrame.Binary(p, finalFragment, _) => Http4sWebSocketFrame.Binary(ByteVector(p), finalFragment)
      case WebSocketFrame.Ping(p)                     => Http4sWebSocketFrame.Ping(ByteVector(p))
      case WebSocketFrame.Pong(p)                     => Http4sWebSocketFrame.Pong(ByteVector(p))
      case WebSocketFrame.Close(code, reason)         => Http4sWebSocketFrame.Close(code, reason).fold(throw _, identity)
    }
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
        case WebSocketFrame.Pong(_) => false
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
      s.evalMap {
        case WebSocketFrame.Ping(payload) => pongs.offer(WebSocketFrame.Pong(payload)).map(_ => none[WebSocketFrame])
        case f                            => f.some.pure[F]
      }.collect { case Some(f) =>
        f
      }
    } else s
  }
}
