package sttp.tapir.server.ziohttp

import io.netty.buffer.Unpooled
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.zio.ZioStreams.Pipe
import sttp.tapir.internal.WebSocketFramesAccumulator
import sttp.tapir.internal.WebSocketFramesAccumulator._
import sttp.tapir.{DecodeResult, WebSocketBodyOutput, WebSocketFrameDecodeFailure}
import sttp.ws.WebSocketFrame
import zhttp.socket.{Socket, WebSocketFrame => ZioWebSocketFrame}
import zio.stream.Stream

private[ziohttp] object ZioWebSockets {
  def pipeToBody[REQ, RESP](
      pipe: Pipe[REQ, RESP],
      o: WebSocketBodyOutput[Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): Socket[Any, Throwable, ZioWebSocketFrame, ZioWebSocketFrame] = {
    Socket.fromFunction[ZioWebSocketFrame] { (zFrame: ZioWebSocketFrame) =>
      val sttpFrames = Stream.succeed(zFrameToFrame(zFrame))
      val concatenated = optionallyConcatenateFrames(sttpFrames, o.concatenateFragmentedFrames)
      val ignorePongs = optionallyIgnorePong(concatenated, o.ignorePong)

      ignorePongs
        .map {
          case _: WebSocketFrame.Close if !o.decodeCloseRequests => None
          case f =>
            o.requests.decode(f) match {
              case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
              case DecodeResult.Value(v)         => Some(v)
            }
        }
        .collectWhileSome
        .via(pipe)
        .map(o.responses.encode)
        .map(frameToZFrame)
    }
  }

  private def zFrameToFrame(f: ZioWebSocketFrame): WebSocketFrame =
    f match {
      case ZioWebSocketFrame.Text(text)            => WebSocketFrame.Text(text, f.isFinal, rsv = None)
      case ZioWebSocketFrame.Binary(buffer)        => WebSocketFrame.Binary(buffer.array(), f.isFinal, rsv = None)
      case ZioWebSocketFrame.Continuation(buffer)  => WebSocketFrame.Binary(buffer.array(), f.isFinal, rsv = None)
      case ZioWebSocketFrame.Ping                  => WebSocketFrame.ping
      case ZioWebSocketFrame.Pong                  => WebSocketFrame.pong
      case ZioWebSocketFrame.Close(status, reason) => WebSocketFrame.Close(status, reason.getOrElse(""))
      case _                                       => WebSocketFrame.Binary(Array.empty[Byte], f.isFinal, rsv = None)
    }

  private def optionallyConcatenateFrames(s: Stream[Nothing, WebSocketFrame], doConcatenate: Boolean): Stream[Nothing, WebSocketFrame] = {
    if (doConcatenate) {
      s.mapAccum(None: Accumulator)(WebSocketFramesAccumulator.acc).collect { case Some(f) => f }
    } else s
  }

  private def optionallyIgnorePong(s: Stream[Nothing, WebSocketFrame], doIgnore: Boolean): Stream[Nothing, WebSocketFrame] = {
    if (doIgnore) {
      s.filter {
        case WebSocketFrame.Pong(_) => false
        case _                      => true
      }
    } else s
  }

  private def frameToZFrame(f: WebSocketFrame): ZioWebSocketFrame =
    f match {
      case WebSocketFrame.Text(p, finalFragment, _)   => ZioWebSocketFrame.Text(p, finalFragment)
      case WebSocketFrame.Binary(p, finalFragment, _) => ZioWebSocketFrame.Binary(Unpooled.wrappedBuffer(p), finalFragment)
      case WebSocketFrame.Ping(_)                     => ZioWebSocketFrame.Ping
      case WebSocketFrame.Pong(_)                     => ZioWebSocketFrame.Pong
      case WebSocketFrame.Close(code, reason)         => ZioWebSocketFrame.Close(code, Some(reason))
    }

}
