package sttp.tapir.client.tests

import cats.effect.unsafe.implicits.global
import sttp.capabilities.Streams
import sttp.tapir.tests.Streaming.in_stream_out_stream

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
  }
}
