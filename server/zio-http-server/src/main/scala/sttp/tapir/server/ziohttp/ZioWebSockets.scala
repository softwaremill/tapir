package sttp.tapir.server.ziohttp
import sttp.capabilities.zio.ZioStreams
import sttp.capabilities.zio.ZioStreams.Pipe
import sttp.tapir.model.WebSocketFrameDecodeFailure
import sttp.tapir.{DecodeResult, WebSocketBodyOutput}
import sttp.ws.WebSocketFrame
import zio.http.{WebSocketFrame => ZWebSocketFrame}
import zio.stream.ZStream
import zio.{Chunk, ZIO}

object ZioWebSockets {
  def pipeToBody[REQ, RESP](
      pipe: Pipe[REQ, RESP],
      o: WebSocketBodyOutput[Pipe[REQ, RESP], REQ, RESP, _, ZioStreams]
  ): F2F = { in =>
    ZStream
      .from(in)
      .map(zFrameToFrame)
      .map {
        case WebSocketFrame.Close(_, _) if !o.decodeCloseRequests => None
        case f =>
          o.requests.decode(f) match {
            case failure: DecodeResult.Failure => throw new WebSocketFrameDecodeFailure(f, failure)
            case DecodeResult.Value(v)         => Some(v)
          }
      }
      .collectWhileSome
      .viaFunction(pipe)
      .map(o.responses.encode)
      .map(frameToZFrame)
      .tap(v => zio.ZIO.succeed(println(v)))
      .runFoldZIO(List.empty[ZWebSocketFrame])((s, ws) => ZIO.succeed(ws +: s))
  }

  private def zFrameToFrame(f: ZWebSocketFrame): WebSocketFrame =
    f match {
      case ZWebSocketFrame.Text(text)            => WebSocketFrame.Text(text, f.isFinal, rsv = None)
      case ZWebSocketFrame.Binary(buffer)        => WebSocketFrame.Binary(buffer.toArray, f.isFinal, rsv = None)
      case ZWebSocketFrame.Continuation(buffer)  => WebSocketFrame.Binary(buffer.toArray, f.isFinal, rsv = None)
      case ZWebSocketFrame.Ping                  => WebSocketFrame.ping
      case ZWebSocketFrame.Pong                  => WebSocketFrame.pong
      case ZWebSocketFrame.Close(status, reason) => WebSocketFrame.Close(status, reason.getOrElse(""))
      case _                                     => WebSocketFrame.Binary(Array.empty[Byte], f.isFinal, rsv = None)
    }

  private def frameToZFrame(f: WebSocketFrame): ZWebSocketFrame =
    f match {
      case WebSocketFrame.Text(p, finalFragment, _)   => ZWebSocketFrame.Text(p, finalFragment)
      case WebSocketFrame.Binary(p, finalFragment, _) => ZWebSocketFrame.Binary(Chunk.fromArray(p), finalFragment)
      case WebSocketFrame.Ping(_)                     => ZWebSocketFrame.Ping
      case WebSocketFrame.Pong(_)                     => ZWebSocketFrame.Pong
      case WebSocketFrame.Close(code, reason)         => ZWebSocketFrame.Close(code, Some(reason))
    }
}
