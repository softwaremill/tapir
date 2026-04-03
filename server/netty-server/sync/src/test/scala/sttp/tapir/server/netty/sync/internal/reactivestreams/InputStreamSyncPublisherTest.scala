package sttp.tapir.server.netty.sync.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Subscriber, Subscription}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.InputStreamRange

import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}

// #5150
class InputStreamSyncPublisherTest extends AnyFreeSpec with Matchers {

  "InputStreamSyncPublisher should not stack overflow on large streams" in {
    // Use a separate thread with a limited stack size to make the stack overflow reproducible
    // and avoid corrupting the test runner's class initialization state.
    val stackSize = 256 * 1024 // 256KB stack - generous but will overflow with ~1000 recursive calls
    val dataSize = 1 * 1024 * 1024 // 1MB
    val chunkSize = 1024 // 1KB chunks -> ~1000 recursive calls

    val data = new Array[Byte](dataSize)
    val result = new AtomicReference[Either[Throwable, Long]](null)

    val thread = new Thread(
      null,
      () => {
        try {
          val totalBytesReceived = new AtomicLong(0)
          val range = InputStreamRange(() => new ByteArrayInputStream(data), range = None)
          val publisher = new InputStreamSyncPublisher(range, chunkSize)
          val error = new AtomicReference[Throwable](null)

          publisher.subscribe(new Subscriber[HttpContent] {
            override def onSubscribe(s: Subscription): Unit = {
              // Request all chunks at once - this triggers the recursive loop
              s.request(Long.MaxValue)
            }

            override def onNext(t: HttpContent): Unit = {
              totalBytesReceived.addAndGet(t.content().readableBytes().toLong)
              t.release(): Unit
            }

            override def onError(t: Throwable): Unit = {
              error.set(t)
            }

            override def onComplete(): Unit = ()
          })

          Option(error.get()) match {
            case Some(e) => result.set(Left(e))
            case None    => result.set(Right(totalBytesReceived.get()))
          }
        } catch {
          case e: StackOverflowError => result.set(Left(e))
          case e: Throwable          => result.set(Left(e))
        }
      },
      "input-stream-sync-publisher-test",
      stackSize
    )
    thread.start()
    thread.join(30000)

    result.get() should not be null
    result.get() match {
      case Left(_: StackOverflowError) =>
        fail("StackOverflowError occurred - the while loop approach should prevent this")
      case Left(e) =>
        fail(s"Unexpected error: ${e.getClass.getName}: ${e.getMessage}")
      case Right(bytes) =>
        bytes shouldBe dataSize.toLong
    }
  }
}
