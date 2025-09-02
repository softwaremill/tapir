package sttp.tapir.client.sttp4.ws

import cats.effect.IO
import sttp.capabilities.WebSockets
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp4.ws.zio._
import _root_.zio.stream.{Stream, ZStream}
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientWebSocketTests
import scala.concurrent.Future

class WebSocketSttpClientZioTests extends WebSocketSttpClientZioTestsSender with ClientWebSocketTests[ZioStreams] {
  override val streams: ZioStreams = ZioStreams
  override def wsToPipe: WebSocketToPipe[WebSockets with ZioStreams] = implicitly

  override def sendAndReceiveLimited[A, B](
      p: Stream[Throwable, A] => Stream[Throwable, B],
      receiveCount: Port,
      as: List[A]
  ): Future[List[B]] =
    unsafeToFuture(
      ZStream(as: _*).viaFunction(p).take(receiveCount.longValue).runCollect.map(_.toList)
    ).future

  webSocketTests()
}
