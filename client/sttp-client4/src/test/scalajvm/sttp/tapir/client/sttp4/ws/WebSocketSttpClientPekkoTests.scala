package sttp.tapir.client.sttp4.ws

import cats.effect.IO
import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import sttp.capabilities.pekko.PekkoStreams
import sttp.capabilities.{Streams, WebSockets}
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.sttp4.ws.WebSocketSttpClientPekkoTestsSender
import sttp.tapir.client.sttp4.ws.pekkohttp._
import sttp.tapir.client.tests.ClientWebSocketTests
import scala.concurrent.Future

class WebSocketSttpClientPekkoTests extends WebSocketSttpClientPekkoTestsSender with ClientWebSocketTests[PekkoStreams] {
  override val streams: Streams[PekkoStreams] = PekkoStreams
  override def wsToPipe: WebSocketToPipe[WebSockets with PekkoStreams] = implicitly

  override def sendAndReceiveLimited[A, B](p: streams.Pipe[A, B], receiveCount: Port, as: List[A]): Future[List[B]] = {
    Source(as).via(p.asInstanceOf[Flow[A, B, Any]]).take(receiveCount.longValue).runWith(Sink.seq).map(_.toList)
  }

  webSocketTests()
}
