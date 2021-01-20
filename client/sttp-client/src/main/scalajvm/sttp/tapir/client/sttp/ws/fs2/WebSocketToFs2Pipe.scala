package sttp.tapir.client.sttp.ws.fs2

import _root_.fs2._
import cats.MonadError
import cats.effect.Concurrent
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.sttp.WebSocketToPipe
import sttp.tapir.{DecodeResult, WebSocketFrameDecodeFailure}
import sttp.ws.{WebSocket, WebSocketFrame}
import cats.syntax.all._
import sttp.tapir.EndpointOutput.WebSocketBody

class WebSocketToFs2Pipe[_F[_]: Concurrent, R <: Fs2Streams[_F] with WebSockets] extends WebSocketToPipe[R] {
  override type S = Fs2Streams[F]
  override type F[X] = _F[X]

  override def apply[REQ, RESP](
      s: Any
  )(ws: WebSocket[F], o: WebSocketBody[Any, REQ, RESP, _, Fs2Streams[F]]): Any = { in: Stream[F, REQ] =>
    val sends = in
      .map(o.requests.encode)
      .evalMap(ws.send(_, isContinuation = false)) // TODO support fragmented frames

    val receives = Stream
      .repeatEval(ws.receive())
      .evalMap[F, Either[Unit, Option[RESP]]] { // left - ignore; right - close or response
        case _: WebSocketFrame.Close if !o.decodeCloseResponses => (Right(None): Either[Unit, Option[RESP]]).pure[F]
        case _: WebSocketFrame.Pong if o.ignorePong             => (Left(()): Either[Unit, Option[RESP]]).pure[F]
        case WebSocketFrame.Ping(p) if o.autoPongOnPing =>
          ws.send(WebSocketFrame.Pong(p)).map(_ => (Left(()): Either[Unit, Option[RESP]]))
        case f =>
          o.responses.decode(f) match {
            case failure: DecodeResult.Failure =>
              implicitly[MonadError[F, Throwable]].raiseError(new WebSocketFrameDecodeFailure(f, failure))
            case DecodeResult.Value(v) => (Right(Some(v)): Either[Unit, Option[RESP]]).pure[F]
          }
      }
      .collect { case Right(d) => d }
      .unNoneTerminate

    sends.drain.merge(receives)
  }
}
