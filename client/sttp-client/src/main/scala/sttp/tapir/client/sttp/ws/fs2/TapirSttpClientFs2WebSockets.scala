package sttp.tapir.client.sttp.ws.fs2

import cats.effect.Concurrent
import sttp.capabilities.{Effect, WebSockets}
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.sttp.WebSocketToPipe

trait TapirSttpClientFs2WebSockets {
  implicit def webSocketsSupportedForFs2Streams[F[_]: Concurrent]: WebSocketToPipe[Fs2Streams[F] with WebSockets] =
    new WebSocketToFs2Pipe[F, Fs2Streams[F] with WebSockets]
  implicit def webSocketsSupportedForFs2StreamsAndEffect[F[_]: Concurrent]: WebSocketToPipe[Effect[F] with Fs2Streams[F] with WebSockets] =
    new WebSocketToFs2Pipe[F, Effect[F] with Fs2Streams[F] with WebSockets]
}
