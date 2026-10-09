package sttp.tapir.server.netty.internal.reactivestreams

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.handler.codec.http.HttpContent
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._

class SimpleSubscriberTest extends AnyFreeSpec with Matchers {
  private implicit def runtime: IORuntime = IORuntime.global

  "completes with an empty body when no chunks are received and no Content-Length is declared" in {
    Stream.empty
      .covaryAll[IO, HttpContent]
      .toUnicastPublisher
      .use { publisher =>
        val subscriber = new SimpleSubscriber(None)
        publisher.subscribe(subscriber)
        IO.blocking(Await.result(subscriber.future, 5.seconds))
      }
      .unsafeRunSync() shouldBe empty
  }
}
