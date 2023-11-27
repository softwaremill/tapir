package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultHttpContent, HttpContent}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.tapir.FileRange

import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

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

    private def readNextChunkIfNeeded(): Unit = {
      if (demand.get() > 0 && !isCompleted.get() && readingInProgress.compareAndSet(false, true)) {
        val pos = position.get()
        val expectedBytes: Int = fileRange.range.flatMap(_.end) match {
          case Some(endPos) if pos + chunkSize > endPos => (endPos - pos + 1).toInt
          case _                                        => chunkSize
        }
        buffer.clear()
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
                  readNextChunkIfNeeded() // Read next chunk if there's more demand
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
