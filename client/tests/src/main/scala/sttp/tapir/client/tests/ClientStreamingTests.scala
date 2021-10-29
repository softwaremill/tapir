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
      // TODO: remove explicit type parameters when https://github.com/lampepfl/dotty/issues/12803 fixed
      send[Unit, streams.BinaryStream, Unit, streams.BinaryStream](in_stream_out_stream(streams), port, (), mkStream("mango cranberry"))
        .map(_.toOption.get)
        .map(rmStream)
        .map(_ shouldBe "mango cranberry")
        .unsafeToFuture()
    }
  }
}
