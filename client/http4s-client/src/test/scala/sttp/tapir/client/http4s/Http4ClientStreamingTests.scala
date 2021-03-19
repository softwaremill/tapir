package sttp.tapir.client.http4s

import cats.effect.IO
import fs2.text
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.tests.ClientStreamingTests

class Http4ClientStreamingTests extends Http4sClientTests[Fs2Streams[IO]] with ClientStreamingTests[Fs2Streams[IO]] {
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  override def mkStream(s: String): streams.BinaryStream = fs2.Stream(s).through(text.utf8Encode)
  override def rmStream(s: streams.BinaryStream): String = s.through(text.utf8Decode).compile.string.unsafeRunSync()

  streamingTests()
}
