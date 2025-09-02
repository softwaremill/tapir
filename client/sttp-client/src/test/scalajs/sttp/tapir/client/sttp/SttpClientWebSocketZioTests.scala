package sttp.tapir.client.sttp

import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp.ws.zio._
import sttp.tapir.client.tests.ClientWebSocketTests
import zio.stream.{Stream, ZStream}
import concurrent.Future

class SttpClientWebSocketZioTests extends SttpClientZioTests[WebSockets with ZioStreams] with ClientWebSocketTests[ZioStreams] {
  override val streams: ZioStreams = ZioStreams
  override def wsToPipe: WebSocketToPipe[WebSockets with ZioStreams] = implicitly

  override def sendAndReceiveLimited[A, B](
      p: Stream[Throwable, A] => Stream[Throwable, B],
      receiveCount: Port,
      as: List[A]
  ): Future[List[B]] = {
    unsafeToFuture(
      ZStream(as: _*).viaFunction(p).take(receiveCount).runCollect.map(_.toList)
    ).future
  }

  webSocketTests()
}
