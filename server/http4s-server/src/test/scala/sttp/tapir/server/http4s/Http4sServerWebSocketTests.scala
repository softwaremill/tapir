package sttp.tapir.server.http4s

import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.tests.ServerWebSocketTests
import sttp.tapir.tests.PortCounter

class Http4sServerWebSocketTests
    extends Http4sServerTests[Fs2Streams[IO] with WebSockets]
    with ServerWebSocketTests[IO, Fs2Streams[IO], HttpRoutes[IO]] {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  override def functionToPipe[A, B](f: A => B): streams.Pipe[A, B] = in => in.map(f)
  webSocketTests()

  override val portCounter: PortCounter = new PortCounter(42000)
}
