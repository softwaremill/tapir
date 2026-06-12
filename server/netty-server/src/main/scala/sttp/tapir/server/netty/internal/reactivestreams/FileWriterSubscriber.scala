package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}

import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{Path, StandardOpenOption}
import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/** A Reactive Streams subscriber which receives chunks of bytes and writes them to a file.
  */
class FileWriterSubscriber(path: Path) extends PromisingSubscriber[Unit, HttpContent] {
  private var subscription: Subscription = _

  /** JDK interface to write asynchronously to a file */
  private var fileChannel: AsynchronousFileChannel = _

  /** Current position in the file */
  @volatile private var position: Long = 0

  // Coordinates the (serially-signalled) upstream with the asynchronous write-completion handler, which runs on the
  // file channel's thread pool. `onComplete` does not require demand, so the publisher may signal it while the last
  // `onNext`'s write is still in flight; closing the channel then would race that write and truncate the file. We
  // therefore close the channel and complete the promise only once the final write has finished. At most one write is
  // ever in flight, because the next chunk is requested only from a write's completion callback.
  private val lock = new Object
  private var writeInProgress = false
  private var upstreamCompleted = false
  private val finished = new AtomicBoolean(false)

  /** Used to signal completion, so that external code can represent writing to a file as Future[Unit] */
  private val resultPromise = Promise[Unit]()

  override def future: Future[Unit] = resultPromise.future

  override def onSubscribe(s: Subscription): Unit = {
    this.subscription = s
    fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    s.request(1)
  }

  override def onNext(httpContent: HttpContent): Unit = {
    val byteBuffer = httpContent.content().nioBuffer()
    lock.synchronized { writeInProgress = true }
    fileChannel.write(
      byteBuffer,
      position,
      (),
      new java.nio.channels.CompletionHandler[Integer, Unit] {
        override def completed(result: Integer, attachment: Unit): Unit = {
          httpContent.release()
          position += result
          val finalizeNow = lock.synchronized {
            writeInProgress = false
            upstreamCompleted
          }
          if (finalizeNow) succeed()
          else subscription.request(1)
        }

        override def failed(exc: Throwable, attachment: Unit): Unit = {
          httpContent.release()
          subscription.cancel()
          fail(exc)
        }
      }
    )
  }

  override def onError(t: Throwable): Unit = fail(t)

  override def onComplete(): Unit = {
    val finalizeNow = lock.synchronized {
      upstreamCompleted = true
      !writeInProgress
    }
    if (finalizeNow) succeed()
  }

  private def succeed(): Unit =
    if (finished.compareAndSet(false, true)) {
      fileChannel.close()
      resultPromise.success(())
    }

  private def fail(t: Throwable): Unit =
    if (finished.compareAndSet(false, true)) {
      if (fileChannel != null) fileChannel.close()
      resultPromise.failure(t)
    }
}

object FileWriterSubscriber {
  def processAll(publisher: Publisher[HttpContent], path: Path, maxBytes: Option[Long]): Future[Unit] = {
    val subscriber = new FileWriterSubscriber(path)
    publisher.subscribe(maxBytes.map(new LimitedLengthSubscriber(_, subscriber)).getOrElse(subscriber))
    subscriber.future
  }

  def processAllBlocking(publisher: Publisher[HttpContent], path: Path, maxBytes: Option[Long]): Unit =
    Await.result(processAll(publisher, path, maxBytes), Duration.Inf)
}
