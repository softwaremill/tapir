package sttp.tapir.client.sttp4

import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.IO
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.{Streams, WebSockets}
import sttp.tapir.client.tests.ClientWebSocketTests
import sttp.tapir.client.sttp4.ws.akkahttp._

class SttpAkkaClientWebSocketTests extends SttpAkkaClientTests[WebSockets with AkkaStreams] with ClientWebSocketTests[AkkaStreams] {
  override val streams: Streams[AkkaStreams] = AkkaStreams
  override def wsToPipe: WebSocketToPipe[WebSockets with AkkaStreams] = implicitly

  override def sendAndReceiveLimited[A, B](p: streams.Pipe[A, B], receiveCount: Port, as: List[A]): IO[List[B]] = {
    val futureResult = Source(as).via(p.asInstanceOf[Flow[A, B, Any]]).take(receiveCount.longValue).runWith(Sink.seq).map(_.toList)
    IO.fromFuture(IO(futureResult))
  }

  webSocketTests()
}
