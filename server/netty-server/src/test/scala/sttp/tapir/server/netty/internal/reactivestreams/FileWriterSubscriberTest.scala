package sttp.tapir.server.netty.internal.reactivestreams

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.server.model.IncompleteRequestBodyException

import java.nio.file.Files
import scala.util.Random

class FileWriterSubscriberTest extends AnyFreeSpec with Matchers {
  private implicit def runtime: IORuntime = IORuntime.global

  private def randomBytes(size: Int): Array[Byte] = {
    val bytes = new Array[Byte](size)
    Random.nextBytes(bytes)
    bytes
  }

  private def writeToFile(bytes: Array[Byte], chunkSize: Int, contentLength: Option[Long]): Either[Throwable, Array[Byte]] = {
    val file = Files.createTempFile("tapir-file-writer-subscriber", ".bin")
    try
      Stream
        .emits(bytes)
        .chunkLimit(chunkSize)
        .map[HttpContent](ch => new DefaultHttpContent(Unpooled.wrappedBuffer(ch.toByteBuffer)))
        .covary[IO]
        .toUnicastPublisher
        .use(publisher => IO.fromFuture(IO(FileWriterSubscriber.processAll(publisher, file, maxBytes = None, contentLength))))
        .attempt
        .map(_.map(_ => Files.readAllBytes(file)))
        .unsafeRunSync()
    finally {
      val _ = Files.deleteIfExists(file)
    }
  }

  "fails when the body ends before the declared Content-Length" in {
    val bytes = randomBytes(4096)
    writeToFile(bytes, chunkSize = 1024, contentLength = Some(10000L)) shouldBe
      Left(IncompleteRequestBodyException(4096, 10000))
  }

  "writes the whole body when it matches the declared Content-Length" in {
    val bytes = randomBytes(4096)
    writeToFile(bytes, chunkSize = 1024, contentLength = Some(4096L)).map(_.toList) shouldBe Right(bytes.toList)
  }
}
