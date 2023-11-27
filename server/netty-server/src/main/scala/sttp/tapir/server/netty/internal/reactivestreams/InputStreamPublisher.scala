package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.tapir.InputStreamRange

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import scala.concurrent.ExecutionContext

class InputStreamPublisher(range: InputStreamRange, chunkSize: Int, blockingEc: ExecutionContext) extends Publisher[HttpContent] {
  override def subscribe(subscriber: Subscriber[_ >: HttpContent]): Unit = {
    if (subscriber == null) throw new NullPointerException("Subscriber cannot be null")
    val subscription = new InputStreamSubscription(subscriber, range, chunkSize)
    subscriber.onSubscribe(subscription)
  }

  private class InputStreamSubscription(subscriber: Subscriber[_ >: HttpContent], range: InputStreamRange, chunkSize: Int)
      extends Subscription {
    private val stream = range.inputStreamFromRangeStart()
    private val demand = new AtomicLong(0L)
    private val position = new AtomicLong(range.range.flatMap(_.start).getOrElse(0L))
    private val isCompleted = new AtomicBoolean(false)
    private val readingInProgress = new AtomicBoolean(false)

    override def request(n: Long): Unit = {
      if (n <= 0) subscriber.onError(new IllegalArgumentException("§3.9: n must be greater than 0"))
      else {
        demand.addAndGet(n)
        readNextChunkIfNeeded()
      }
    }

    private def readNextChunkIfNeeded(): Unit = {
      if (demand.get() > 0 && !isCompleted.get() && readingInProgress.compareAndSet(false, true)) {
        val pos = position.get()
        val expectedBytes: Int = range.range.flatMap(_.end) match {
          case Some(endPos) if pos + chunkSize > endPos => (endPos - pos + 1).toInt
          case _                                        => chunkSize
        }
        Future {
          stream.readNBytes(expectedBytes) // Blocking I/IO
        }(blockingEc)
          .onComplete {
            case Success(bytes) =>
              val bytesRead = bytes.length
              if (bytesRead == 0) {
                cancel()
                subscriber.onComplete()
              } else {
                position.addAndGet(bytesRead.toLong)
                subscriber.onNext(new DefaultHttpContent(Unpooled.wrappedBuffer(bytes)))
                if (bytesRead < expectedBytes) {
                  cancel()
                  subscriber.onComplete()
                } else {
                  demand.decrementAndGet()
                  readingInProgress.set(false)
                  readNextChunkIfNeeded()
                }
              }
            case Failure(e) =>
              stream.close()
              subscriber.onError(e)
          }(blockingEc)
      }
    }

    override def cancel(): Unit = {
      isCompleted.set(true)
      stream.close()
    }
  }
}
