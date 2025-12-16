package sttp.tapir.client.http4s

import cats.effect.IO

import fs2.text
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.client.tests.ClientStreamingTests
import cats.effect.unsafe.IORuntime

class Http4ClientStreamingTests extends Http4sClientTests[Fs2Streams[IO]] with ClientStreamingTests[Fs2Streams[IO]] {
  private implicit val ioRT: IORuntime = cats.effect.unsafe.implicits.global
  override val streams: Fs2Streams[IO] = Fs2Streams[IO]
  override def mkStream(s: String): streams.BinaryStream = fs2.Stream(s).through(text.utf8.encode)
  override def rmStream(s: streams.BinaryStream): String = s.through(text.utf8.decode).compile.string.unsafeRunSync()

  streamingTests()
}
