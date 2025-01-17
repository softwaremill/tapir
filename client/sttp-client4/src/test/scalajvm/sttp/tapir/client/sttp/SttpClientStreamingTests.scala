package sttp.tapir.client.sttp

import cats.effect.IO
import cats.implicits._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.tests.ClientStreamingTests

class SttpClientStreamingTests extends SttpClientTests[Fs2Streams[IO]] with ClientStreamingTests[Fs2Streams[IO]] {
  override def wsToPipe: WebSocketToPipe[Fs2Streams[IO]] = implicitly
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]

  override def mkStream(s: String): fs2.Stream[IO, Byte] = fs2.Stream.emits(s.getBytes("utf-8"))
  override def rmStream(s: fs2.Stream[IO, Byte]): String =
    s.through(fs2.text.utf8Decode)
      .compile
      .foldMonoid
      .unsafeRunSync()

  streamingTests()
}
