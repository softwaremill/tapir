package sttp.tapir.client.sttp4

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.client.sttp4.WebSocketToPipe
import sttp.tapir.client.tests.ClientStreamingTests
import zio.Chunk
import zio.stream.{Stream, ZPipeline, ZStream}

class SttpClientStreamingZioTests extends SttpClientZioTests[ZioStreams] with ClientStreamingTests[ZioStreams] {
  override def wsToPipe: WebSocketToPipe[ZioStreams] = implicitly
  override val streams: ZioStreams = ZioStreams

  override def mkStream(s: String): Stream[Throwable, Byte] = ZStream.fromChunk(Chunk.fromArray(s.getBytes("utf-8")))
  override def rmStream(s: Stream[Throwable, Byte]): String = {
    unsafeRun(
      s.via(ZPipeline.utf8Decode).runCollect.map(_.fold("")(_ ++ _))
    )
  }

  streamingTests()
}
