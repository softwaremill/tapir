package sttp.tapir.server.ziohttp
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.zio.ZioStreams.Pipe
import sttp.tapir.DecodeResult
import sttp.tapir.WebSocketBodyOutput
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.ws.{WebSocketFrame => SttpWebSocketFrame}
import zio.Chunk
import zio.Duration.fromScala
import zio.Schedule
import zio.ZIO
import zio.http.ChannelEvent.Read
import zio.http.WebSocketChannelEvent
import zio.http.{WebSocketFrame => ZioWebSocketFrame}
import zio.stream
import zio.stream.ZStream

import scala.concurrent.duration.FiniteDuration

object ZioWebSockets {

  def pipeToBody[REQ, RESP](
      pipe: Pipe[REQ, RESP],
      o: WebSocketBodyOutput[Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): WebSocketHandler = {
    { (in: stream.Stream[Throwable, WebSocketChannelEvent]) =>
      {
        for {
          pongs <- zio.Queue.bounded[SttpWebSocketFrame](1)
          sttpFrames = in.map(zWebSocketChannelEventToFrame).collectSome
          concatenated = optionallyConcatenate(sttpFrames, o.concatenateFragmentedFrames)
          ignoredPongs = optionallyIgnorePongs(concatenated, o.ignorePong)
          autoPongs = optionallyAutoPongOnPing(ignoredPongs, pongs, o.autoPongOnPing)
          autoPing = optionallyAutoPing(o.autoPing)
          closeStream = stream.ZStream.from[SttpWebSocketFrame](SttpWebSocketFrame.close)
          intermediateStream = autoPongs
            .map {
              case _: SttpWebSocketFrame.Close if !o.decodeCloseRequests => None
              case f =>
                o.requests.decode(f) match {
                  case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
                  case DecodeResult.Value(v)         => Some(v)
                }
            }
            .collectWhileSome
            .viaFunction(pipe)
            .map(o.responses.encode)
            .mergeHaltLeft(stream.ZStream.fromQueue[SttpWebSocketFrame](pongs, 1))
            .mergeHaltLeft(autoPing) ++ closeStream
          sendReceiveStream = intermediateStream.map(frameToZWebSocketChannelEvent)
        } yield sendReceiveStream
      }
    }
  }

  private def zWebSocketChannelEventToFrame(channelEvent: WebSocketChannelEvent): Option[SttpWebSocketFrame] =
    channelEvent match {
      case Read(f @ ZioWebSocketFrame.Text(text))           => Some(SttpWebSocketFrame.Text(text, f.isFinal, rsv = None))
      case Read(f @ ZioWebSocketFrame.Binary(buffer))       => Some(SttpWebSocketFrame.Binary(buffer.toArray, f.isFinal, rsv = None))
      case Read(f @ ZioWebSocketFrame.Continuation(buffer)) => Some(SttpWebSocketFrame.Binary(buffer.toArray, f.isFinal, rsv = None))
      case Read(ZioWebSocketFrame.Ping)                     => Some(SttpWebSocketFrame.ping)
      case Read(ZioWebSocketFrame.Pong)                     => Some(SttpWebSocketFrame.pong)
      case Read(ZioWebSocketFrame.Close(status, reason))    => Some(SttpWebSocketFrame.Close(status, reason.getOrElse("")))
      case Read(f)                                          => Some(SttpWebSocketFrame.Binary(Array.empty[Byte], f.isFinal, rsv = None))
      case _                                                => None
    }

  private def frameToZWebSocketChannelEvent(f: SttpWebSocketFrame): WebSocketChannelEvent =
    f match {
      case SttpWebSocketFrame.Text(p, finalFragment, _)   => Read(ZioWebSocketFrame.Text(p, finalFragment))
      case SttpWebSocketFrame.Binary(p, finalFragment, _) => Read(ZioWebSocketFrame.Binary(Chunk.fromArray(p), finalFragment))
      case SttpWebSocketFrame.Ping(_)                     => Read(ZioWebSocketFrame.Ping)
      case SttpWebSocketFrame.Pong(_)                     => Read(ZioWebSocketFrame.Pong)
      case SttpWebSocketFrame.Close(code, reason)         => Read(ZioWebSocketFrame.Close(code, Some(reason)))
    }

  private def optionallyIgnorePongs(
      sttpFrames: ZStream[Any, Throwable, SttpWebSocketFrame],
      ignorePong: Boolean
  ): ZStream[Any, Throwable, SttpWebSocketFrame] = {
    sttpFrames
      .filter {
        case _: SttpWebSocketFrame.Pong if ignorePong => false
        case _                                        => true
      }
  }

  private def optionallyAutoPing(
      autoPing: Option[(FiniteDuration, SttpWebSocketFrame.Ping)]
  ): ZStream[Any, Nothing, SttpWebSocketFrame] = {
    autoPing match {
      case Some((duration, ping)) =>
        stream.ZStream
          .from(ping)
          .repeat(Schedule.fixed(fromScala(duration)))
      case None => stream.ZStream.empty
    }
  }

  private def optionallyAutoPongOnPing(
      sttpFrames: ZStream[Any, Throwable, SttpWebSocketFrame],
      pongs: zio.Queue[SttpWebSocketFrame],
      autoPongOnPing: Boolean
  ): ZStream[Any, Throwable, SttpWebSocketFrame] = {
    if (autoPongOnPing) {
      sttpFrames.mapZIO {
        case SttpWebSocketFrame.Ping(payload) if autoPongOnPing =>
          pongs.offer(SttpWebSocketFrame.Pong(payload)).as(Option.empty[SttpWebSocketFrame])
        case f => ZIO.succeed(Some(f))
      }.collectSome
    } else sttpFrames
  }

  private def optionallyConcatenate(
      sttpFrames: ZStream[Any, Throwable, SttpWebSocketFrame],
      concatenate: Boolean
  ): ZStream[Any, Throwable, SttpWebSocketFrame] = {
    if (concatenate) {
      type Accumulator = Option[Either[Array[Byte], String]]

      sttpFrames
        .mapAccum(None: Accumulator) {
          case (None, f: SttpWebSocketFrame.Ping)                                  => (None, Some(f))
          case (None, f: SttpWebSocketFrame.Pong)                                  => (None, Some(f))
          case (None, f: SttpWebSocketFrame.Close)                                 => (None, Some(f))
          case (None, f: SttpWebSocketFrame.Data[_]) if f.finalFragment            => (None, Some(f))
          case (None, f: SttpWebSocketFrame.Text)                                  => (Some(Right(f.payload)), None)
          case (None, f: SttpWebSocketFrame.Binary)                                => (Some(Left(f.payload)), None)
          case (Some(Left(acc)), f: SttpWebSocketFrame.Binary) if f.finalFragment  => (None, Some(f.copy(payload = acc ++ f.payload)))
          case (Some(Left(acc)), f: SttpWebSocketFrame.Binary) if !f.finalFragment => (Some(Left(acc ++ f.payload)), None)
          case (Some(Right(acc)), f: SttpWebSocketFrame.Text) if f.finalFragment =>
            (None, Some(f.copy(payload = acc + f.payload)))
          case (Some(Right(acc)), f: SttpWebSocketFrame.Text) if !f.finalFragment =>
            (Some(Right(acc + f.payload)), None)

          case (acc, f) => throw new IllegalStateException(s"Cannot accumulate web socket frames. Accumulator: $acc, frame: $f.")
        }
        .collectSome
    } else sttpFrames
  }

}
