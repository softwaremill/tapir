package sttp.tapir.client.sttp4.ws

import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import _root_.fs2._
import fs2._
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientWebSocketTests
import scala.concurrent.Future

class WebSocketSttpClientTests extends WebSocketSttpClientTestsSender with ClientWebSocketTests[Fs2Streams[IO]] {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  override def wsToPipe: WebSocketToPipe[WebSockets with Fs2Streams[IO]] = implicitly

  override def sendAndReceiveLimited[A, B](p: Pipe[IO, A, B], receiveCount: Port, as: List[A]): Future[List[B]] = {
    Stream(as: _*).through(p).take(receiveCount.longValue).compile.toList.unsafeToFuture()
  }

  webSocketTests()

}
