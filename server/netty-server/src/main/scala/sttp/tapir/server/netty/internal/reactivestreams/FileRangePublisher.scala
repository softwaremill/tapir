package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.tapir.FileRange

import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

/** A Reactive Streams publisher which emits chunks of HttpContent read from a given file.
  */
class FileRangePublisher(fileRange: FileRange, chunkSize: Int) extends Publisher[HttpContent] {
  override def subscribe(subscriber: Subscriber[_ >: HttpContent]): Unit = {
    if (subscriber == null) throw new NullPointerException("Subscriber cannot be null")
    val subscription = new FileRangeSubscription(subscriber, fileRange, chunkSize)
    subscriber.onSubscribe(subscription)
  }

  private class FileRangeSubscription(subscriber: Subscriber[_ >: HttpContent], fileRange: FileRange, chunkSize: Int) extends Subscription {
    private lazy val channel: AsynchronousFileChannel = AsynchronousFileChannel.open(fileRange.file.toPath(), StandardOpenOption.READ)
    private val demand = new AtomicLong(0L)
    private val position = new AtomicLong(fileRange.range.flatMap(_.start).getOrElse(0L))
    private val buffer: ByteBuffer = ByteBuffer.allocate(chunkSize)
    private val isCompleted = new AtomicBoolean(false)
    private val readingInProgress = new AtomicBoolean(false)

    override def request(n: Long): Unit = {
      if (n <= 0) subscriber.onError(new IllegalArgumentException("§3.9: n must be greater than 0"))
      else {
        demand.addAndGet(n)
        readNextChunkIfNeeded()
      }
    }

    /** Can be called multiple times by request(n), or concurrently by channel.read() callback. The readingInProgress check ensures that
      * calls are serialized. A channel.read() operation will be started only if another isn't running. This method is non-blocking.
      */
    private def readNextChunkIfNeeded(): Unit = {
      if (demand.get() > 0 && !isCompleted.get() && readingInProgress.compareAndSet(false, true)) {
        val pos = position.get()
        val expectedBytes: Int = fileRange.range.flatMap(_.end) match {
          case Some(endPos) if pos + chunkSize > endPos => (endPos - pos + 1).toInt
          case _                                        => chunkSize
        }
        buffer.clear()
        // Async call, so readNextChunkIfNeeded() finishes immediately after firing this
        channel.read(
          buffer,
          pos,
          null,
          new CompletionHandler[Integer, Void] {
            override def completed(bytesRead: Integer, attachment: Void): Unit = {
              if (bytesRead == -1) {
                cancel()
                subscriber.onComplete()
              } else {
                val bytesToRead = Math.min(bytesRead, expectedBytes)
                // The buffer is modified only by one thread at a time, because only one channel.read()
                // is running at a time, and because buffer.clear() calls before the read are guarded
                // by readingInProgress.compareAndSet.
                buffer.flip()
                val bytes = new Array[Byte](bytesToRead)
                buffer.get(bytes)
                position.addAndGet(bytesToRead.toLong)
                subscriber.onNext(new DefaultHttpContent(Unpooled.wrappedBuffer(bytes)))
                if (bytesToRead < expectedBytes) {
                  cancel()
                  subscriber.onComplete()
                } else {
                  demand.decrementAndGet()
                  readingInProgress.set(false)
                  // Either this call, or a call from request(n) will win the race to
                  // actually start a new read.
                  readNextChunkIfNeeded()
                }
              }
            }

            override def failed(exc: Throwable, attachment: Void): Unit = {
              subscriber.onError(exc)
            }
          }
        )
      }
    }

    override def cancel(): Unit = {
      isCompleted.set(true)
      channel.close()
    }
  }
}
