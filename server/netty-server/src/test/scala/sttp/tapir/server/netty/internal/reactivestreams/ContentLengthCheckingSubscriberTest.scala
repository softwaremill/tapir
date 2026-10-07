package sttp.tapir.server.netty.internal.reactivestreams

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.Subscriber
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.server.model.IncompleteRequestBodyException

class ContentLengthCheckingSubscriberTest extends AnyFreeSpec with Matchers {
  private implicit def runtime: IORuntime = IORuntime.global

  private def readAll(body: String, contentLength: Option[Long]): Either[Throwable, String] =
    Stream
      .emits(body.getBytes)
      .chunkLimit(4)
      .map[HttpContent](ch => new DefaultHttpContent(Unpooled.wrappedBuffer(ch.toArray)))
      .covary[IO]
      .toUnicastPublisher
      .use { publisher =>
        val subscriber = new SimpleSubscriber(contentLength)
        publisher.subscribe(contentLength.fold[Subscriber[HttpContent]](subscriber)(new ContentLengthCheckingSubscriber(_, subscriber)))
        IO.fromFuture(IO(subscriber.future))
      }
      .attempt
      .map(_.map(new String(_)))
      .unsafeRunSync()

  "fails when the body ends before the declared Content-Length" in {
    readAll("only a part", contentLength = Some(100L)) shouldBe Left(IncompleteRequestBodyException(11, 100))
  }

  "fails when no body bytes are received, but some are declared" in {
    readAll("", contentLength = Some(100L)) shouldBe Left(IncompleteRequestBodyException(0, 100))
  }

  "passes the whole body when it matches the declared Content-Length" in {
    readAll("the whole body", contentLength = Some(14L)) shouldBe Right("the whole body")
  }

  "completes with an empty body when no bytes are received and no Content-Length is declared" in {
    readAll("", contentLength = None) shouldBe Right("")
  }
}
