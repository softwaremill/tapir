package sttp.tapir.client.tests

import sttp.capabilities.Streams
import sttp.tapir.tests.Streaming.in_stream_out_stream
import sttp.tapir.tests.Streaming.in_stream_out_string
import sttp.tapir.tests.Streaming.in_string_stream_out_either_stream_string
import sttp.tapir.tests.Streaming.in_string_stream_out_either_error_stream
import sttp.tapir.tests.Streaming.in_string_out_stream_and_header

trait ClientStreamingTests[S] { this: ClientTests[S] =>
  val streams: Streams[S]

  def mkStream(s: String): streams.BinaryStream
  def rmStream(s: streams.BinaryStream): String

  def streamingTests(): Unit = {
    test(in_stream_out_stream(streams).showDetail) {
      send(in_stream_out_stream(streams), port, (), mkStream("mango cranberry"))
        .map(_.toOption.get)
        .map(rmStream)
        .map(_ shouldBe "mango cranberry")
        .unsafeToFuture()
    }

    test(in_stream_out_string(streams).showDetail) {
      send(in_stream_out_string(streams), port, (), mkStream("mango cranberry"))
        .map(_.toOption.get)
        .map(_ shouldBe "mango cranberry")
        .unsafeToFuture()
    }

    test(in_string_stream_out_either_error_stream(streams).showDetail + ", success case") {
      send(in_string_stream_out_either_error_stream(streams), port, (), (false, mkStream("mango cranberry")))
        .map(_.toOption.get)
        .map(rmStream)
        .map(_ shouldBe "mango cranberry")
        .unsafeToFuture()
    }

    test(in_string_stream_out_either_error_stream(streams).showDetail + ", error case") {
      send(in_string_stream_out_either_error_stream(streams), port, (), (true, mkStream("mango cranberry")))
        .map(_ shouldBe Left("error as requested"))
        .unsafeToFuture()
    }

    test(in_string_out_stream_and_header(streams).showDetail) {
      send(in_string_out_stream_and_header(streams), port, (), ("apple", "mango cranberry"))
        .map(_.toOption.get)
        .map { case (header, body) =>
          header shouldBe "apple"
          body
        }
        .map(rmStream)
        .map(_ shouldBe "mango cranberry")
        .unsafeToFuture()
    }
  }
}
