package sttp.tapir.server.http4s

import cats.effect.IO
import org.http4s.HttpRoutes
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.tests.ServerStreamingTests

class Http4sServerStreamingTests extends Http4sServerTests[Fs2Streams[IO]] with ServerStreamingTests[IO, Fs2Streams[IO], HttpRoutes[IO]] {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  streamingTests()
}
