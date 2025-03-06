//package sttp.tapir.client.sttp4.ws
//
//import cats.effect.IO
//import sttp.capabilities.WebSockets
//import sttp.capabilities.zio.ZioStreams
//import sttp.tapir.client.sttp4.ws.zio.*
//import sttp.tapir.client.sttp4.ws.zio.stream.{Stream, ZStream}
//import sttp.tapir.client.sttp4.WebSocketToPipe
//import sttp.tapir.client.sttp4.basic.BasicSttpClientZioTestsSender
//import sttp.tapir.client.tests.ClientWebSocketTests
//
//class SttpClientWebSocketZioTests extends BasicSttpClientZioTestsSender[WebSockets with ZioStreams] with ClientWebSocketTests[ZioStreams] {
//  override val streams: ZioStreams = ZioStreams
//  override def wsToPipe: WebSocketToPipe[WebSockets with ZioStreams] = implicitly
//
//  override def sendAndReceiveLimited[A, B](
//      p: Stream[Throwable, A] => Stream[Throwable, B],
//      receiveCount: Port,
//      as: List[A]
//  ): IO[List[B]] = IO.fromFuture(
//    IO.delay {
//      unsafeToFuture(
//        ZStream(as: _*).viaFunction(p).take(receiveCount.longValue).runCollect.map(_.toList)
//      ).future
//    }
//  )
//
//  webSocketTests()
//}
