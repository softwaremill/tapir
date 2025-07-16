package sttp.tapir.client.sttp

import org.apache.pekko.stream.scaladsl.{Flow, Sink, Source}
import cats.effect.IO
import sttp.capabilities.pekko.PekkoStreams
import sttp.capabilities.{Streams, WebSockets}
import sttp.tapir.client.tests.ClientWebSocketTests
import sttp.tapir.client.sttp.ws.pekkohttp._
import scala.concurrent.Future

class SttpClientWebSocketPekkoTests extends SttpClientPekkoTests[WebSockets with PekkoStreams] with ClientWebSocketTests[PekkoStreams] {
  override val streams: Streams[PekkoStreams] = PekkoStreams
  override def wsToPipe: WebSocketToPipe[WebSockets with PekkoStreams] = implicitly

  override def sendAndReceiveLimited[A, B](p: streams.Pipe[A, B], receiveCount: Port, as: List[A]): Future[List[B]] = {
    Source(as).via(p.asInstanceOf[Flow[A, B, Any]]).take(receiveCount).runWith(Sink.seq).map(_.toList)
  }

  webSocketTests()
}
