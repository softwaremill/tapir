package sttp.tapir.server.netty.sync.internal.reactivestreams

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.tapir.InputStreamRange

import java.io.InputStream
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

/** A synchronous variant of InputStreamPublisher, tailored for the Identity monad / direct-style server. Uses a while loop instead of
  * recursive monadic calls to avoid StackOverflowError on large streams.
  */
class InputStreamSyncPublisher(
    range: InputStreamRange,
    chunkSize: Int
) extends Publisher[HttpContent] {
  override def subscribe(subscriber: Subscriber[_ >: HttpContent]): Unit = {
    if (subscriber == null) throw new NullPointerException("Subscriber cannot be null")
    val subscription = new InputStreamSyncSubscription(subscriber, range, chunkSize)
    subscriber.onSubscribe(subscription)
  }

  private class InputStreamSyncSubscription(subscriber: Subscriber[_ >: HttpContent], range: InputStreamRange, chunkSize: Int)
      extends Subscription {
    private lazy val stream: InputStream = range.inputStreamFromRangeStart()
    private val demand = new AtomicLong(0L)
    private val position = new AtomicLong(range.range.flatMap(_.start).getOrElse(0L))
    private val isCompleted = new AtomicBoolean(false)
    private val readingInProgress = new AtomicBoolean(false)

    override def request(n: Long): Unit = {
      if (n <= 0) {
        cancel()
        subscriber.onError(new IllegalArgumentException("§3.9: n must be greater than 0"))
      } else {
        addDemand(n)
        readNextChunkIfNeeded()
      }
    }

    /** Add demand using saturating addition, capping at Long.MaxValue to prevent overflow. */
    private def addDemand(n: Long): Unit = {
      demand.getAndUpdate(current => if (current > Long.MaxValue - n) Long.MaxValue else current + n)
      ()
    }

    private def readNextChunkIfNeeded(): Unit = {
      // Everything here is synchronous and blocking, which is against the reactive streams spec
      while (demand.get() > 0 && !isCompleted.get() && readingInProgress.compareAndSet(false, true)) {
        try {
          var continue = true
          while (continue) {
            val pos = position.get()
            val expectedBytes: Int = range.range.flatMap(_.end) match {
              case Some(endPos) if pos + chunkSize > endPos => (endPos - pos + 1).toInt
              case _                                        => chunkSize
            }
            val bytes = stream.readNBytes(expectedBytes)
            val bytesRead = bytes.length
            if (bytesRead == 0) {
              cancel()
              subscriber.onComplete()
              continue = false
            } else {
              position.addAndGet(bytesRead.toLong)
              subscriber.onNext(new DefaultHttpContent(Unpooled.wrappedBuffer(bytes)))
              if (bytesRead < expectedBytes) {
                cancel()
                subscriber.onComplete()
                continue = false
              } else {
                demand.decrementAndGet()
                continue = demand.get() > 0 && !isCompleted.get()
              }
            }
          }
          readingInProgress.set(false)
        } catch {
          case e: Throwable =>
            isCompleted.set(true)
            try stream.close()
            catch {
              case e2: Throwable => e.addSuppressed(e2)
            }
            subscriber.onError(e)
            return
        }
      }
    }

    override def cancel(): Unit = {
      isCompleted.set(true)
      try stream.close()
      catch {
        case e2: Throwable => // ignore
      }
    }
  }
}
