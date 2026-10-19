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

import scala.concurrent.Await
import scala.concurrent.duration._

class ContentLengthCheckingSubscriberTest extends AnyFreeSpec with Matchers {
  private implicit def runtime: IORuntime = IORuntime.global

  private def readAll(body: String, contentLength: Long): Either[Throwable, String] =
    Stream
      .emits(body.getBytes)
      .chunkLimit(4)
      .map[HttpContent](ch => new DefaultHttpContent(Unpooled.wrappedBuffer(ch.toArray)))
      .covary[IO]
      .toUnicastPublisher
      .use { publisher =>
        val subscriber = new SimpleSubscriber(Some(contentLength))
        publisher.subscribe(new ContentLengthCheckingSubscriber(contentLength, subscriber))
        IO.blocking(Await.result(subscriber.future, 5.seconds))
      }
      .attempt
      .map(_.map(new String(_)))
      .unsafeRunSync()

  "fails when the body ends before the declared Content-Length" in {
    readAll("only a part", contentLength = 100L) shouldBe Left(IncompleteRequestBodyException(11, 100))
  }

  "passes the whole body when it matches the declared Content-Length" in {
    readAll("the whole body", contentLength = 14L) shouldBe Right("the whole body")
  }
}
