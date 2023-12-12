package sttp.tapir.server.http4s

import cats.effect.{Temporal, Sync}
import cats.NonEmptyParallel
import cats.syntax.all._
import cats.{Monad, NonEmptyParallel, Applicative}
import fs2._
import fs2.concurrent.Channel
import org.http4s.websocket.{WebSocketFrame => Http4sWebSocketFrame}
import scodec.bits.ByteVector
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame

private[http4s] object Http4sWebSockets {
  def pipeToBody[F[_]: NonEmptyParallel: Temporal, REQ, RESP](
                                   pipe: Pipe[F, REQ, RESP],
                                   o: WebSocketBodyOutput[Pipe[F, REQ, RESP], REQ, RESP, _, Fs2Streams[F]]
                                 ): F[Pipe[F, Http4sWebSocketFrame, Http4sWebSocketFrame]] = {
    if ((!o.concatenateFragmentedFrames) && (!o.ignorePing) && (!o.ignorePong) && (!o.autoPongOnPing) && o.autoPing.isEmpty) {
      // fast track: lift Http4sWebSocketFrames into REQ, run through pipe, convert RESP back to Http4sWebSocketFrame

      (in: Stream[F, Http4sWebSocketFrame]) =>
        optionallyDecodeClose(in, o.decodeCloseRequests)
          .map { http4sFrame =>
            val f = http4sFrameToFrame(http4sFrame)
            o.requests.decode(f) match {
              case x: DecodeResult.Value[REQ] => x.v
              case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
            }
          }
          .through(pipe)
          .mapChunks(_.map(r => frameToHttp4sFrame(o.responses.encode(r))))
          .append(Stream(frameToHttp4sFrame(WebSocketFrame.close)))
    }.pure[F] else {
      // concurrently merge business logic response, autoPings, autoPongOnPing
      // use fs2.Channel to perform the merge (more efficient than Stream#mergeHaltL / Stream#parJoin)

      Channel.bounded[F, Chunk[Http4sWebSocketFrame]](64).map { c =>
        (in: Stream[F, Http4sWebSocketFrame]) =>
          val decodeClose = optionallyDecodeClose(in, o.decodeCloseRequests)
          val sttpFrames = decodeClose.map(http4sFrameToFrame)
          val concatenated = optionallyConcatenateFrames(sttpFrames, o.concatenateFragmentedFrames)
          val autoPongs = optionallyAutoPong(concatenated, c, o.autoPongOnPing)
          val ignorePingPongs = optionallyIgnorePingPong(autoPongs, o.ignorePing, o.ignorePong)
          val autoPings = o.autoPing match {
            case Some((interval, frame)) => (c.send(Chunk(frameToHttp4sFrame(frame))) >> Temporal[F].sleep(interval)).foreverM[Unit]
            case None => Applicative[F].unit
          }

          val outputProducer = ignorePingPongs
            .map { f =>
              o.requests.decode(f) match {
                case x: DecodeResult.Value[REQ] => x.v
                case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
              }
            }
            .through(pipe)
            .chunks
            .foreach(chunk => c.send(chunk.map(r => frameToHttp4sFrame(o.responses.encode(r)))).void)
            .compile
            .drain

          c.stream
            .concurrently(Stream.exec((outputProducer >> c.close.void, autoPings).parTupled.void))
            .append(Stream(Chunk(frameToHttp4sFrame(WebSocketFrame.close))))
            .unchunks
      }
    }
  }

  private def http4sFrameToFrame(f: Http4sWebSocketFrame): WebSocketFrame =
    f match {
      case t: Http4sWebSocketFrame.Text => WebSocketFrame.Text(t.str, t.last, None)
      case x: Http4sWebSocketFrame.Ping => WebSocketFrame.Ping(x.data.toArray)
      case x: Http4sWebSocketFrame.Pong => WebSocketFrame.Pong(x.data.toArray)
      case c: Http4sWebSocketFrame.Close => WebSocketFrame.Close(c.closeCode, "")
      case _ => WebSocketFrame.Binary(f.data.toArray, f.last, None)
    }

  private def frameToHttp4sFrame(w: WebSocketFrame): Http4sWebSocketFrame =
    w match {
      case x: WebSocketFrame.Text => Http4sWebSocketFrame.Text(x.payload, x.finalFragment)
      case x: WebSocketFrame.Binary => Http4sWebSocketFrame.Binary(ByteVector(x.payload), x.finalFragment)
      case x: WebSocketFrame.Ping => Http4sWebSocketFrame.Ping(ByteVector(x.payload))
      case x: WebSocketFrame.Pong => Http4sWebSocketFrame.Pong(ByteVector(x.payload))
      case x: WebSocketFrame.Close => Http4sWebSocketFrame.Close(x.statusCode, x.reasonText).fold(throw _, identity)
    }

  private def optionallyConcatenateFrames[F[_]](s: Stream[F, WebSocketFrame], doConcatenate: Boolean): Stream[F, WebSocketFrame] =
    if (doConcatenate) {
      type Accumulator = Option[Either[Array[Byte], String]]

      s.mapAccumulate(None: Accumulator) {
        case (None, f: WebSocketFrame.Ping) => (None, Some(f))
        case (None, f: WebSocketFrame.Pong) => (None, Some(f))
        case (None, f: WebSocketFrame.Close) => (None, Some(f))
        case (None, f: WebSocketFrame.Data[_]) if f.finalFragment => (None, Some(f))
        case (Some(Left(acc)), f: WebSocketFrame.Binary) if f.finalFragment => (None, Some(f.copy(payload = acc ++ f.payload)))
        case (Some(Left(acc)), f: WebSocketFrame.Binary) if !f.finalFragment => (Some(Left(acc ++ f.payload)), None)
        case (Some(Right(acc)), f: WebSocketFrame.Text) if f.finalFragment => (None, Some(f.copy(payload = acc + f.payload)))
        case (Some(Right(acc)), f: WebSocketFrame.Text) if !f.finalFragment => (Some(Right(acc + f.payload)), None)
        case (acc, f) => throw new IllegalStateException(s"Cannot accumulate web socket frames. Accumulator: $acc, frame: $f.")
      }.collect { case (_, Some(f)) => f }
    } else s

  private def optionallyIgnorePingPong[F[_]](s: Stream[F, WebSocketFrame], ignorePing: Boolean, ignorePong: Boolean): Stream[F, WebSocketFrame] =
    (ignorePing, ignorePong) match {
      case (false, false) => s
      case (true, false) =>
        s.filter {
          case _: WebSocketFrame.Ping => false
          case _ => true
        }
      case (false, true) =>
        s.filter {
          case _: WebSocketFrame.Pong => false
          case _ => true
        }
      case (true, true) =>
        s.filter {
          case _: WebSocketFrame.Ping => false
          case _: WebSocketFrame.Pong => false
          case _ => true
        }
    }

  private def optionallyAutoPong[F[_] : Monad](
                                                s: Stream[F, WebSocketFrame],
                                                c: Channel[F, Chunk[Http4sWebSocketFrame]],
                                                doAuto: Boolean
                                              ): Stream[F, WebSocketFrame] =
    if (doAuto) {
      val trueF = true.pure[F]
      s.evalFilter {
        case ping: WebSocketFrame.Ping => c.send(Chunk(frameToHttp4sFrame(WebSocketFrame.Pong(ping.payload)))).map(_ => false)
        case _ => trueF
      }
    } else s

  private def optionallyDecodeClose[F[_]](s: Stream[F, Http4sWebSocketFrame], doDecodeClose: Boolean): Stream[F, Http4sWebSocketFrame] =
    if (!doDecodeClose) {
      s.takeWhile {
        case _: Http4sWebSocketFrame.Close => false
        case _ => true
      }
    } else s
}
