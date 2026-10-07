package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}

import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{Path, StandardOpenOption}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

/** A Reactive Streams subscriber which receives chunks of bytes and writes them to a file.
  */
class FileWriterSubscriber(path: Path) extends PromisingSubscriber[Unit, HttpContent] {
  import FileWriterSubscriber._

  private var subscription: Subscription = _

  /** JDK interface to write asynchronously to a file */
  private var fileChannel: AsynchronousFileChannel = _

  /** Current position in the file */
  @volatile private var position: Long = 0

  // Lock-free coordination between the (serially-signalled) upstream and the asynchronous write-completion handler,
  // which runs on the file channel's thread pool. `onComplete` does not require demand, so the publisher may signal it
  // while the last `onNext`'s write is still in flight; closing the channel then would race that write and truncate the
  // file. At most one write is ever in flight (the next chunk is requested only from a write's completion callback), so
  // the only contention is between that callback and `onComplete`. A CAS on `state` decides which of them performs the
  // finalization, and `terminate*` (a single atomic move to Finished) makes the channel close + promise completion
  // happen exactly once.
  private val state = new AtomicInteger(Idle)

  /** Used to signal completion, so that external code can represent writing to a file as Future[Unit] */
  private val resultPromise = Promise[Unit]()

  override def future: Future[Unit] = resultPromise.future

  override def onSubscribe(s: Subscription): Unit = {
    this.subscription = s
    fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    s.request(1)
  }

  override def onNext(httpContent: HttpContent): Unit =
    // Normally state is Idle here. If the CAS fails we've already terminated - a compliant publisher may still deliver
    // an onNext after we cancelled (rule 2.8) - so drop the chunk rather than write to an already-closed channel.
    if (state.compareAndSet(Idle, Writing)) {
      val byteBuffer = httpContent.content().nioBuffer()
      fileChannel.write(
        byteBuffer,
        position,
        (),
        new java.nio.channels.CompletionHandler[Integer, Unit] {
          override def completed(result: Integer, attachment: Unit): Unit = {
            httpContent.release()
            position += result
            // If upstream completed during this write (state moved to Completing), finalize now; otherwise ask for more.
            if (state.compareAndSet(Writing, Idle)) subscription.request(1)
            else terminateSuccess()
          }

          override def failed(exc: Throwable, attachment: Unit): Unit = {
            httpContent.release()
            subscription.cancel()
            terminateFailure(exc)
          }
        }
      )
    } else {
      val _ = httpContent.release()
    }

  override def onError(t: Throwable): Unit = terminateFailure(t)

  override def onComplete(): Unit =
    // If a write is still in flight, defer finalization to its completion callback; otherwise finalize now.
    if (!state.compareAndSet(Writing, Completing)) terminateSuccess()

  private def terminateSuccess(): Unit =
    if (state.getAndSet(Finished) != Finished) {
      fileChannel.close()
      resultPromise.success(())
    }

  private def terminateFailure(t: Throwable): Unit =
    if (state.getAndSet(Finished) != Finished) {
      if (fileChannel != null) fileChannel.close()
      resultPromise.failure(t)
    }
}

object FileWriterSubscriber {
  // States for the lock-free finalization handshake between the write-completion callback and `onComplete`.
  private final val Idle = 0 // no write in flight, upstream not completed
  private final val Writing = 1 // a write is in flight, upstream not completed
  private final val Completing = 2 // a write is in flight, upstream completed - the write callback will finalize
  private final val Finished = 3 // channel closed and promise completed

  def processAll(publisher: Publisher[HttpContent], path: Path, maxBytes: Option[Long]): Future[Unit] = {
    val subscriber = new FileWriterSubscriber(path)
    publisher.subscribe(maxBytes.map(new LimitedLengthSubscriber(_, subscriber)).getOrElse(subscriber))
    subscriber.future
  }

  def processAllBlocking(publisher: Publisher[HttpContent], path: Path, maxBytes: Option[Long]): Unit =
    Await.result(processAll(publisher, path, maxBytes), Duration.Inf)
}
