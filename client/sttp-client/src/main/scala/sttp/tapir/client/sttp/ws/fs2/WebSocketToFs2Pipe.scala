package sttp.tapir.client.sttp.ws.fs2

import _root_.fs2._
import cats.MonadError
import cats.effect.Concurrent
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.sttp.WebSocketToPipe
import sttp.tapir.{DecodeResult, WebSocketBodyOutput, WebSocketFrameDecodeFailure}
import sttp.ws.{WebSocket, WebSocketFrame}
import cats.syntax.all._

class WebSocketToFs2Pipe[_F[_]: Concurrent, R <: Fs2Streams[_F] with WebSockets] extends WebSocketToPipe[R] {
  override type S = Fs2Streams[F]
  override type F[X] = _F[X]

  override def apply[REQ, RESP](
      s: Any
  )(ws: WebSocket[F], o: WebSocketBodyOutput[Any, REQ, RESP, _, Fs2Streams[F]]): Any = { in: Stream[F, REQ] =>
    val sends = in
      .map(o.requests.encode)
      .evalMap(ws.send(_, isContinuation = false)) // TODO support fragmented frames

    val receives = Stream
      .repeatEval(ws.receive())
      .evalMap[F, Option[RESP]] {
        case _: WebSocketFrame.Close if !o.decodeCloseResponses => none.pure[F]
        case _: WebSocketFrame.Pong if o.ignorePong             => none.pure[F]
        case WebSocketFrame.Ping(p) if o.autoPongOnPing =>
          ws.send(WebSocketFrame.Pong(p)).map(_ => none)
        case f =>
          o.responses.decode(f) match {
            case failure: DecodeResult.Failure =>
              implicitly[MonadError[F, Throwable]].raiseError(new WebSocketFrameDecodeFailure(f, failure))
            case DecodeResult.Value(v) => v.some.pure[F]
          }
      }
      .unNoneTerminate

    sends.drain.merge(receives)
  }
}
