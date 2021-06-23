package sttp.tapir.client.tests

import sttp.capabilities.Streams
import sttp.tapir.DecodeResult
import sttp.tapir.tests.{in_stream_out_stream, not_existing_endpoint}

trait ClientStreamingTests[S] { this: ClientTests[S] =>
  val streams: Streams[S]

  def mkStream(s: String): streams.BinaryStream
  def rmStream(s: streams.BinaryStream): String

  def streamingTests(): Unit = {
    test(in_stream_out_stream(streams).showDetail) {
      rmStream(
        // TODO: remove explicit type parameters when https://github.com/lampepfl/dotty/issues/12803 fixed
        send[streams.BinaryStream, Unit, streams.BinaryStream](in_stream_out_stream(streams), port, mkStream("mango cranberry"))
          .unsafeRunSync()
          .toOption
          .get
      ) shouldBe "mango cranberry"
    }
  }
}
