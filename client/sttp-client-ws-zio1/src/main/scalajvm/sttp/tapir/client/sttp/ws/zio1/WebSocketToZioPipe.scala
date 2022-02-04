package sttp.tapir.client.sttp.ws.zio1

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp.WebSocketToPipe
import sttp.tapir.{DecodeResult, WebSocketBodyOutput, WebSocketFrameDecodeFailure}
import sttp.ws.{WebSocket, WebSocketFrame}
import zio.Task
import zio.stream.Stream

import scala.reflect.ClassTag

class WebSocketToZioPipe[R <: ZioStreams with WebSockets] extends WebSocketToPipe[R] {
  override type S = ZioStreams
  override type F[X] = Task[X]

  override def apply[REQ, RESP](s: Any)(ws: WebSocket[F], o: WebSocketBodyOutput[Any, REQ, RESP, _, ZioStreams]): Any = {
    (in: Stream[Throwable, REQ]) =>
      val sends = in
        .map(o.requests.encode)
        .mapM(ws.send(_, isContinuation = false)) // TODO support fragmented frames

      def decode(frame: WebSocketFrame): F[Either[Unit, Option[RESP]]] =
        o.responses.decode(frame) match {
          case failure: DecodeResult.Failure =>
            Task.fail(new WebSocketFrameDecodeFailure(frame, failure))
          case DecodeResult.Value(v) =>
            Task.right[Option[RESP]](Some(v))
        }

      def raiseBadAccumulator[T](acc: WebSocketFrame, current: WebSocketFrame): F[T] =
        Task.fail(
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
        }).map(None -> _)
        else
          (acc match {
            case None       => Task.some(frame)
            case Some(x: A) => Task.some(f(x, frame))
            case Some(bad)  => raiseBadAccumulator(bad, frame)
          }).map(acc => acc -> Left(()))

      val receives = Stream
        .repeatEffect(ws.receive())
        .mapAccumM[Any, Throwable, Option[WebSocketFrame], Either[Unit, Option[RESP]]](
          None
        ) { // left - ignore; right - close or response
          case (acc, _: WebSocketFrame.Close) if !o.decodeCloseResponses =>
            Task.succeed(acc -> Right(None))
          case (acc, _: WebSocketFrame.Pong) if o.ignorePong =>
            Task.succeed(acc -> Left(()))
          case (acc, WebSocketFrame.Ping(p)) if o.autoPongOnPing =>
            ws.send(WebSocketFrame.Pong(p)).as(acc -> Left(()))
          case (prev, frame @ WebSocketFrame.Text(_, last, _)) =>
            concatOrDecode(prev, frame, last)((l, r) => r.copy(payload = l.payload + r.payload))
          case (prev, frame @ WebSocketFrame.Binary(_, last, _)) =>
            concatOrDecode(prev, frame, last)((l, r) => r.copy(payload = l.payload ++ r.payload))
          case (_, frame) =>
            Task.fail(
              new WebSocketFrameDecodeFailure(
                frame,
                DecodeResult.Error(
                  "Unrecognised frame type",
                  new Exception(s"Unrecognised frame type: ${frame.getClass}")
                )
              )
            )
        }
        .collectRight
        .collectWhileSome

      sends.drain.merge(receives)
  }
}
