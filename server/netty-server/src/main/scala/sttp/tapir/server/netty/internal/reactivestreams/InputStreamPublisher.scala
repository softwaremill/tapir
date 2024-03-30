package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.InputStreamRange
import sttp.tapir.server.netty.internal.RunAsync

import java.io.InputStream
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import scala.util.Try

class InputStreamPublisher[F[_]](
    range: InputStreamRange,
    chunkSize: Int,
    runAsync: RunAsync[F]
)(implicit
    monad: MonadError[F]
) extends Publisher[HttpContent] {
  override def subscribe(subscriber: Subscriber[_ >: HttpContent]): Unit = {
    if (subscriber == null) throw new NullPointerException("Subscriber cannot be null")
    val subscription = new InputStreamSubscription(subscriber, range, chunkSize)
    subscriber.onSubscribe(subscription)
  }

  private class InputStreamSubscription(subscriber: Subscriber[_ >: HttpContent], range: InputStreamRange, chunkSize: Int)
      extends Subscription {
    private lazy val stream: InputStream = range.inputStreamFromRangeStart()
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

    /** Non-blocking by itself, starts an asynchronous operation with blocking stream.readNBytes. Can be called multiple times by
      * request(n), or concurrently by onComplete callback. The readingInProgress check ensures that calls are serialized. A
      * stream.readNBytes operation will be started only if another isn't running.
      */
    private def readNextChunkIfNeeded(): Unit = {
      if (demand.get() > 0 && !isCompleted.get() && readingInProgress.compareAndSet(false, true)) {
        val pos = position.get()
        val expectedBytes: Int = range.range.flatMap(_.end) match {
          case Some(endPos) if pos + chunkSize > endPos => (endPos - pos + 1).toInt
          case _                                        => chunkSize
        }

        // Note: the effect F may be Id, in which case everything here will be synchronous and blocking
        // (which technically is against the reactive streams spec).
        runAsync(
          monad
            .blocking(
              stream.readNBytes(expectedBytes)
            )
            .map { bytes =>
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
            }
            .handleError { case e =>
              val _ = Try(stream.close())
              monad.unit(subscriber.onError(e))
            }
        )
      }
    }

    override def cancel(): Unit = {
      isCompleted.set(true)
      val _ = Try(stream.close())
    }
  }
}
