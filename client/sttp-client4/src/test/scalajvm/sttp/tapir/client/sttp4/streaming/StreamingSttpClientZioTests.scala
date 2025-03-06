package sttp.tapir.client.sttp4.streaming

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp4.streaming.StreamingSttpClientZioTestsSender
import sttp.tapir.client.tests.ClientStreamingTests
import zio.Chunk
import zio.stream.{Stream, ZPipeline, ZStream}

class StreamingSttpClientZioTests extends StreamingSttpClientZioTestsSender with ClientStreamingTests[ZioStreams] {
  override val streams: ZioStreams = ZioStreams

  override def mkStream(s: String): Stream[Throwable, Byte] = ZStream.fromChunk(Chunk.fromArray(s.getBytes("utf-8")))
  override def rmStream(s: Stream[Throwable, Byte]): String = {
    unsafeRun(
      s.via(ZPipeline.utf8Decode).runCollect.map(_.fold("")(_ ++ _))
    )
  }

  streamingTests()
}
