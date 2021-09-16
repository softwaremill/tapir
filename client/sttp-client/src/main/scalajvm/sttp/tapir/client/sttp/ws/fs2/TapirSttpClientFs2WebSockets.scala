package sttp.tapir.client.sttp.ws.fs2

import cats.effect.Concurrent
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.sttp.WebSocketToPipe

trait TapirSttpClientFs2WebSockets {
  implicit def webSocketsSupportedForFs2Streams[F[_]: Concurrent]: WebSocketToPipe[Fs2Streams[F] with WebSockets] =
    new WebSocketToFs2Pipe[F, Fs2Streams[F] with WebSockets]
}
