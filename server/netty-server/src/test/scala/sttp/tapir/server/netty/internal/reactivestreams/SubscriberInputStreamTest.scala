package sttp.tapir.server.netty.internal.reactivestreams

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultHttpContent
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalactic.source.Position

import scala.util.Random

class SubscriberInputStreamTest extends AnyFreeSpec with Matchers {
  private implicit def runtime: IORuntime = IORuntime.global

  private def testReading(
      size: Int,
      chunkLimit: Int = 1024,
      maxBufferedChunks: Int = 1
  )(implicit pos: Position): Unit = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)

    val publisherResource = Stream
      .emits(bytes)
      .chunkLimit(chunkLimit)
      .map(ch => new DefaultHttpContent(Unpooled.wrappedBuffer(ch.toByteBuffer)))
      .covary[IO]
      .toUnicastPublisher

    val io = publisherResource.use { publisher =>
      IO {
        val subscriberInputStream = new SubscriberInputStream(maxBufferedChunks)
        publisher.subscribe(subscriberInputStream)
        subscriberInputStream.readAllBytes() shouldBe bytes
      }
    }

    io.unsafeRunSync()
  }

  "empty stream" in {
    testReading(0)
  }

  "single chunk stream" in {
    testReading(10)
  }

  "multiple chunks" in {
    testReading(100, 10)
  }

  "multiple chunks with larger buffer" in {
    testReading(100, 10, maxBufferedChunks = 5)
  }

  "multiple chunks with smaller last chunk" in {
    testReading(105, 10)
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
