package sttp.tapir.client.sttp.ws.fs2

import _root_.fs2._
import cats.MonadError
import cats.effect.Concurrent
import cats.syntax.all._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.sttp.WebSocketToPipe
import sttp.tapir.{DecodeResult, WebSocketBodyOutput, WebSocketFrameDecodeFailure}
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.reflect.ClassTag

class WebSocketToFs2Pipe[_F[_]: Concurrent, R <: Fs2Streams[_F] with WebSockets] extends WebSocketToPipe[R] {
  override type S = Fs2Streams[F]
  override type F[X] = _F[X]

  override def apply[REQ, RESP](s: Any)(ws: WebSocket[F], o: WebSocketBodyOutput[Any, REQ, RESP, _, Fs2Streams[F]]): Any = {
    in: Stream[F, REQ] =>
      val sends = in
        .map(o.requests.encode)
        .evalMap(ws.send(_, isContinuation = false)) // TODO support fragmented frames

      def decode(frame: WebSocketFrame) =
        o.responses.decode(frame) match {
          case failure: DecodeResult.Failure =>
            MonadError[F, Throwable].raiseError(
              new WebSocketFrameDecodeFailure(frame, failure)
            )
          case DecodeResult.Value(v) => (Right(Some(v)): Either[Unit, Option[RESP]]).pure[F]
        }

      def raiseBadAccumulator(acc: WebSocketFrame, current: WebSocketFrame) =
        MonadError[F, Throwable].raiseError(
          new WebSocketFrameDecodeFailure(
            current,
            DecodeResult.Error(
              "Bad frame sequence",
              new Exception(
                s"Invalid accumulator frame: $acc, it can't be concatenated with $current"
              )
            )
          )
        )

      def concatOrDecode[A <: WebSocketFrame: ClassTag](
          acc: Option[WebSocketFrame],
          frame: A,
          last: Boolean
      )(f: (A, A) => A): F[(Option[WebSocketFrame], Either[Unit, Option[RESP]])] =
        if (last) (acc match {
          case None       => decode(frame)
          case Some(x: A) => decode(f(x, frame))
          case Some(bad)  => raiseBadAccumulator(bad, frame)
        }).map(none[WebSocketFrame] -> _)
        else
          (acc match {
            case None       => frame.some.pure[F]
            case Some(x: A) => f(x, frame).some.pure[F]
            case Some(bad)  => raiseBadAccumulator(bad, frame)
          }).map(acc => acc -> ().asLeft)

      val receives = Stream
        .repeatEval(ws.receive())
        .evalMapAccumulate[F, Option[WebSocketFrame], Either[Unit, Option[RESP]]](
          none[WebSocketFrame]
        ) { // left - ignore; right - close or response
          case (acc, _: WebSocketFrame.Close) if !o.decodeCloseResponses =>
            (acc -> (Right(None): Either[Unit, Option[RESP]])).pure[F]
          case (acc, _: WebSocketFrame.Pong) if o.ignorePong =>
            (acc -> (Left(()): Either[Unit, Option[RESP]])).pure[F]
          case (acc, WebSocketFrame.Ping(p)) if o.autoPongOnPing =>
            ws.send(WebSocketFrame.Pong(p))
              .map(_ => acc -> (Left(()): Either[Unit, Option[RESP]]))
          case (prev, frame @ WebSocketFrame.Text(_, last, _)) =>
            concatOrDecode(prev, frame, last)((l, r) => r.copy(payload = l.payload + r.payload))
          case (prev, frame @ WebSocketFrame.Binary(_, last, _)) =>
            concatOrDecode(prev, frame, last)((l, r) => r.copy(payload = l.payload ++ r.payload))
          case (_, frame) =>
            MonadError[F, Throwable].raiseError(
              new WebSocketFrameDecodeFailure(
                frame,
                DecodeResult.Error(
                  "Unrecognised frame type",
                  new Exception(s"Unrecognised frame type: ${frame.getClass}")
                )
              )
            )
        }
        .collect { case (_, Right(d)) => d }
        .unNoneTerminate

      sends.drain.merge(receives)
  }
}
