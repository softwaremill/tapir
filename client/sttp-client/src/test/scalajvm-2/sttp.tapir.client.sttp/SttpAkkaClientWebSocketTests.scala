package sttp.tapir.client.sttp

import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.IO
import sttp.capabilities.akka.AkkaStreams
import sttp.capabilities.{Streams, WebSockets}
import sttp.tapir.client.tests.ClientWebSocketTests
import sttp.tapir.client.sttp.ws.akkahttp._
import scala.concurrent.Future

class SttpAkkaClientWebSocketTests extends SttpAkkaClientTests[WebSockets with AkkaStreams] with ClientWebSocketTests[AkkaStreams] {
  override val streams: Streams[AkkaStreams] = AkkaStreams
  override def wsToPipe: WebSocketToPipe[WebSockets with AkkaStreams] = implicitly

  override def sendAndReceiveLimited[A, B](p: streams.Pipe[A, B], receiveCount: Port, as: List[A]): Future[List[B]] = {
    Source(as).via(p.asInstanceOf[Flow[A, B, Any]]).take(receiveCount).runWith(Sink.seq).map(_.toList)
  }

  webSocketTests()
}
