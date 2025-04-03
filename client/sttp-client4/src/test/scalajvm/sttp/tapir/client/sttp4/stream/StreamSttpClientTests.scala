package sttp.tapir.client.sttp4.stream

import cats.effect.IO
import cats.implicits._
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.tests.ClientStreamingTests

class StreamSttpClientTests extends StreamSttpClientTestsSender with ClientStreamingTests[Fs2Streams[IO]] {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]

  override def mkStream(s: String): fs2.Stream[IO, Byte] = fs2.Stream.emits(s.getBytes("utf-8"))
  override def rmStream(s: fs2.Stream[IO, Byte]): String =
    s.through(fs2.text.utf8.decode)
      .compile
      .foldMonoid
      .unsafeRunSync()

  streamingTests()
}
