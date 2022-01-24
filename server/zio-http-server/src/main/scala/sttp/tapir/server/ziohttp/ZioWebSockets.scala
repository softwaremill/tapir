package sttp.tapir.server.ziohttp

import io.netty.buffer.Unpooled
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.zio.ZioStreams.Pipe
import sttp.tapir.{DecodeResult, WebSocketBodyOutput, WebSocketFrameDecodeFailure}
import sttp.ws.WebSocketFrame
import zhttp.socket.{Socket, WebSocketFrame => ZioWebSocketFrame}
import zio.stream.Stream

private[ziohttp] object ZioWebSockets {
  def pipeToBody[F[_], REQ, RESP](
      pipe: Pipe[REQ, RESP],
      o: WebSocketBodyOutput[Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): Socket[Any, Throwable, ZioWebSocketFrame, ZioWebSocketFrame] = {
    Socket.fromFunction[ZioWebSocketFrame] { zFrame: ZioWebSocketFrame =>
      Stream
        .succeed(zFrameToFrame(zFrame))
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
      case t @ ZioWebSocketFrame.Text(text)           => WebSocketFrame.Text(text, t.isFinal, rsv = None)
      case b @ ZioWebSocketFrame.Binary(buffer)       => WebSocketFrame.Binary(buffer.array(), b.isFinal, rsv = None)
      case c @ ZioWebSocketFrame.Continuation(buffer) => WebSocketFrame.Binary(buffer.array(), c.isFinal, rsv = None)
      case ZioWebSocketFrame.Ping                     => WebSocketFrame.ping
      case ZioWebSocketFrame.Pong                     => WebSocketFrame.pong
      case ZioWebSocketFrame.Close(status, reason)    => WebSocketFrame.Close(status, reason.getOrElse("normal closure"))
      case _                                          => WebSocketFrame.Binary(Array.empty[Byte], f.isFinal, rsv = None)
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
