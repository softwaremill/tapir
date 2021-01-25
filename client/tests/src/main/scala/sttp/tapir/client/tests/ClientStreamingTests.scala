package sttp.tapir.client.tests

import sttp.capabilities.Streams
import sttp.tapir.DecodeResult
import sttp.tapir.tests.{in_stream_out_stream, not_existing_endpoint}

trait ClientStreamingTests[S, F[_]] { this: ClientTests[S, F] =>
  val streams: Streams[S]

  def mkStream(s: String): streams.BinaryStream
  def rmStream(s: streams.BinaryStream): String

  def streamingTests(): Unit = {
    test(in_stream_out_stream(streams).showDetail) {
      rmStream(
        send(in_stream_out_stream(streams), port, mkStream("mango cranberry"))
          .unsafeRunSync()
          .toOption
          .get
      ) shouldBe "mango cranberry"
    }

    test("not existing endpoint, with error output not matching 404") {
      safeSend(not_existing_endpoint, port, ()).unsafeRunSync() should matchPattern {
        case DecodeResult.Error(_, _: IllegalArgumentException) =>
      }
    }
  }
}
