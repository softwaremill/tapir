package sttp.tapir.client.sttp4

import cats.effect.IO
import fs2._
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.tests.ClientWebSocketTests
import sttp.tapir.client.sttp4.ws.fs2._
import sttp.tapir.client.sttp4.WebSocketToPipe

class SttpClientWebSocketTests extends SttpClientTests[WebSockets with Fs2Streams[IO]] with ClientWebSocketTests[Fs2Streams[IO]] {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  override def wsToPipe: WebSocketToPipe[WebSockets with Fs2Streams[IO]] = implicitly

  override def sendAndReceiveLimited[A, B](p: Pipe[IO, A, B], receiveCount: Port, as: List[A]): IO[List[B]] = {
    Stream(as: _*).through(p).take(receiveCount.longValue).compile.toList
  }

  webSocketTests()

}
