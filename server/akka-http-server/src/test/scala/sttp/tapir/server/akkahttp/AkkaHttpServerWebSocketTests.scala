package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import sttp.capabilities.WebSockets
import sttp.capabilities.akka.AkkaStreams
import sttp.tapir.server.tests.ServerWebSocketTests
import sttp.tapir.tests.PortCounter

import scala.concurrent.Future

class AkkaHttpServerWebSocketTests
    extends AkkaHttpServerTests[AkkaStreams with WebSockets]
    with ServerWebSocketTests[Future, AkkaStreams, Route] {
  override val streams: AkkaStreams = AkkaStreams
  override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = Flow.fromFunction(f)
  webSocketTests()

  override val portCounter: PortCounter = new PortCounter(40000)
}
