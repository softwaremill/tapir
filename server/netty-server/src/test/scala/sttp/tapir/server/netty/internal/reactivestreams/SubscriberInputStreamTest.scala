package sttp.tapir.server.netty.internal.reactivestreams

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultHttpContent
import org.scalactic.source.Position
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import java.io.InputStream
import scala.annotation.tailrec
import scala.util.Random

class SubscriberInputStreamTest extends AnyFreeSpec with Matchers {
  private implicit def runtime: IORuntime = IORuntime.global

  private def readAll(is: InputStream, batchSize: Int): Array[Byte] = {
    val buf = Unpooled.buffer(batchSize)
    @tailrec def writeLoop(): Array[Byte] = buf.writeBytes(is, batchSize) match {
      case -1 => buf.array().take(buf.readableBytes())
      case _  => writeLoop()
    }
    writeLoop()
  }

  private def testReading(
      totalSize: Int,
      publishedChunkLimit: Int,
      readBatchSize: Int,
      maxBufferedChunks: Int = 1
  )(implicit pos: Position): Unit = {
    val bytes = new Array[Byte](totalSize)
    Random.nextBytes(bytes)

    val publisherResource = Stream
      .emits(bytes)
      .chunkLimit(publishedChunkLimit)
      .map(ch => new DefaultHttpContent(Unpooled.wrappedBuffer(ch.toByteBuffer)))
      .covary[IO]
      .toUnicastPublisher

    val io = publisherResource.use { publisher =>
      IO {
        val subscriberInputStream = new SubscriberInputStream(maxBufferedChunks)
        publisher.subscribe(subscriberInputStream)
        readAll(subscriberInputStream, readBatchSize) shouldBe bytes
      }
    }

    io.unsafeRunSync()
  }

  "empty stream" in {
    testReading(totalSize = 0, publishedChunkLimit = 1024, readBatchSize = 1024)
  }

  "single chunk stream, one read batch" in {
    testReading(totalSize = 10, publishedChunkLimit = 1024, readBatchSize = 1024)
  }

  "single chunk stream, multiple read batches" in {
    testReading(totalSize = 100, publishedChunkLimit = 1024, readBatchSize = 10)
    testReading(totalSize = 100, publishedChunkLimit = 1024, readBatchSize = 11)
  }

  "multiple chunks, read batch larger than chunk" in {
    testReading(totalSize = 100, publishedChunkLimit = 10, readBatchSize = 1024)
    testReading(totalSize = 105, publishedChunkLimit = 10, readBatchSize = 1024)
  }

  "multiple chunks, read batch smaller than chunk" in {
    testReading(totalSize = 105, publishedChunkLimit = 20, readBatchSize = 17)
    testReading(totalSize = 105, publishedChunkLimit = 20, readBatchSize = 7)
    testReading(totalSize = 105, publishedChunkLimit = 20, readBatchSize = 5)
  }

  "multiple chunks, large publishing buffer" in {
    testReading(totalSize = 105, publishedChunkLimit = 10, readBatchSize = 1024, maxBufferedChunks = 5)
  }

  "closing the stream should cancel the subscription" in {
    var canceled = false

    val publisherResource =
      Stream
        .emits(Array.fill(1024)(0.toByte))
        .chunkLimit(100)
        .map(ch => new DefaultHttpContent(Unpooled.wrappedBuffer(ch.toByteBuffer)))
        .covary[IO]
        .onFinalizeCase {
          case Resource.ExitCase.Canceled => IO { canceled = true }
          case _                          => IO.unit
        }
        .toUnicastPublisher

    publisherResource
      .use(publisher =>
        IO {
          val stream = new SubscriberInputStream()
          publisher.subscribe(stream)

          stream.readNBytes(120).length shouldBe 120
          stream.close()
        }
      )
      .unsafeRunSync()

    canceled shouldBe true
  }
}
