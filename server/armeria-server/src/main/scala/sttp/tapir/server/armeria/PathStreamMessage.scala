package sttp.tapir.server.armeria

import com.linecorp.armeria.common.HttpData
import com.linecorp.armeria.common.stream._
import com.linecorp.armeria.common.util.Exceptions
import com.linecorp.armeria.internal.common.stream.InternalStreamMessageUtil.{containsNotifyCancellation, containsWithPooledObjects}
import com.linecorp.armeria.internal.common.stream.NoopSubscription
import com.linecorp.armeria.internal.shaded.guava.math.LongMath
import com.linecorp.armeria.internal.shaded.guava.primitives.Ints
import io.netty.buffer.{ByteBuf, ByteBufAllocator, ByteBufUtil}
import io.netty.util.concurrent.EventExecutor
import java.io.IOException
import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.{Path, StandardOpenOption}
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.reactivestreams.{Subscriber, Subscription}
import org.slf4j.{Logger, LoggerFactory}
import sttp.tapir.server.armeria.PathStreamMessage.logger

// Forked from https://github.com/line/armeria/blob/d1237acd5f1dc660b53305e17774bf29f76766c7/core/src/main/java/com/linecorp/armeria/common/stream/PathStreamMessage.java
// and modified to read a file with a specific range.
// TODO(ikhoon): Upstream's PathStreamMessage did not support partical read.
//               Remove this class if https://github.com/line/armeria/pull/4058 is merged.
private[armeria] object PathStreamMessage {
  private val logger: Logger = LoggerFactory.getLogger(classOf[PathStreamMessage])
  private val DEFAULT_FILE_BUFFER_SIZE: Int = 8192

  def apply(path: Path, start: Long, end: Long): PathStreamMessage = {
    val normalizedEnd: Long = if (end == -1) Long.MaxValue else end
    val bufferSize = math.min(Ints.saturatedCast(normalizedEnd - start), DEFAULT_FILE_BUFFER_SIZE)

    new PathStreamMessage(path, ByteBufAllocator.DEFAULT, start, normalizedEnd, bufferSize)
  }
}

private final class PathStreamMessage(
    val path: Path,
    val alloc: ByteBufAllocator,
    val start: Long,
    val end: Long,
    val bufferSize: Int
) extends StreamMessage[HttpData] {
  private val completionFuture: CompletableFuture[Void] = new CompletableFuture[Void]
  private val subscribed: AtomicBoolean = new AtomicBoolean()

  @volatile private var pathSubscription: PathSubscription = _

  override def isOpen: Boolean = !completionFuture.isDone

  override def isEmpty: Boolean = {
    if (isOpen) {
      false
    } else {
      val pathSubscription = this.pathSubscription
      pathSubscription == null || pathSubscription.position == 0
    }
  }

  override def demand: Long = {
    val pathSubscription = this.pathSubscription
    if (pathSubscription != null) {
      pathSubscription.requested
    } else {
      0
    }
  }

  override def whenComplete: CompletableFuture[Void] = completionFuture

  override def subscribe(subscriber: Subscriber[_ >: HttpData], executor: EventExecutor, options: SubscriptionOption*): Unit = {
    if (!subscribed.compareAndSet(false, true)) {
      subscriber.onSubscribe(NoopSubscription.get)
      subscriber.onError(new IllegalStateException("Only single subscriber is allowed!"))
    } else {
      if (executor.inEventLoop) {
        subscribe0(subscriber, executor, options: _*)
      } else {
        executor.execute(() => subscribe0(subscriber, executor, options: _*))
      }
    }
  }

  private def subscribe0(subscriber: Subscriber[_ >: HttpData], executor: EventExecutor, options: SubscriptionOption*): Unit = {
    var fileChannel: AsynchronousFileChannel = null
    var success = false
    try {
      fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.READ)
      if (fileChannel.size == 0) {
        subscriber.onSubscribe(NoopSubscription.get)
        if (completionFuture.isCompletedExceptionally)
          completionFuture.handle[Unit]((_: Void, cause: Throwable) => {
            subscriber.onError(Exceptions.peel(cause))
          })
        else {
          subscriber.onComplete()
          completionFuture.complete(null)
        }
        return
      }
      success = true
    } catch {
      case e: IOException =>
        subscriber.onSubscribe(NoopSubscription.get)
        subscriber.onError(e)
        completionFuture.completeExceptionally(e)
        return
    } finally {
      if (!success && fileChannel != null) {
        try {
          fileChannel.close()
        } catch {
          case e: IOException =>
            logger.warn("Unexpected exception while closing {}.", Array(fileChannel, e): _*)
        }
      }
    }

    val pathSubscription: PathSubscription =
      new PathSubscription(
        fileChannel,
        subscriber,
        executor,
        start,
        end,
        bufferSize,
        containsNotifyCancellation(options: _*),
        containsWithPooledObjects(options: _*)
      )
    this.pathSubscription = pathSubscription
    subscriber.onSubscribe(pathSubscription)
  }

  override def abort(): Unit = {
    abort(AbortedStreamException.get)
  }

  override def abort(cause: Throwable): Unit = {
    val pathSubscription: PathSubscription = this.pathSubscription
    if (pathSubscription != null) {
      pathSubscription.maybeCloseFileChannel()
      pathSubscription.close(Some(cause))
    }
    val _ = completionFuture.completeExceptionally(cause)
  }

  private class PathSubscription(
      val fileChannel: AsynchronousFileChannel,
      var downstream: Subscriber[_ >: HttpData],
      val executor: EventExecutor,
      @volatile var position: Long,
      val end: Long,
      val bufferSize: Int,
      val notifyCancellation: Boolean,
      val withPooledObjects: Boolean
  ) extends Subscription
      with CompletionHandler[Integer, ByteBuf] {

    private var reading: Boolean = false
    private var closed: Boolean = false
    @volatile var requested: Long = 0L

    override def request(n: Long): Unit = {
      if (n <= 0L) {
        downstream.onError(
          new IllegalArgumentException(
            "Rule §3.9 violated: non-positive subscription " +
              "requests " + "are forbidden."
          )
        )
        cancel()
      } else request0(n)
    }

    private def request0(n: Long): Unit = {
      val oldRequested = this.requested
      if (oldRequested == Long.MaxValue) {
        // no-op
      } else {
        this.requested = if (n == Long.MaxValue) Long.MaxValue else LongMath.saturatedAdd(oldRequested, n)
        if (oldRequested > 0) {
          // PathSubscription is reading a file.
          // New requests will be handled by 'completed(Integer, ByteBuf)'.
        } else {
          read()
        }
      }
    }

    private def read(): Unit = {
      if (!reading && !closed && requested > 0) {
        requested -= 1
        reading = true
        val position: Long = this.position
        val bufferSize: Int = Math.min(this.bufferSize, Ints.saturatedCast(end - position))
        val buffer: ByteBuf = alloc.buffer(bufferSize)
        fileChannel.read(buffer.nioBuffer(0, bufferSize), position, buffer, this)
      }
    }

    override def cancel(): Unit = {
      if (executor.inEventLoop) {
        cancel0()
      } else {
        executor.execute(() => cancel0())
      }
    }

    private def cancel0(): Unit = {
      if (closed) return
      closed = true
      if (!reading) maybeCloseFileChannel()
      val cause: CancelledSubscriptionException = CancelledSubscriptionException.get
      if (notifyCancellation) downstream.onError(cause)
      completionFuture.completeExceptionally(cause)
      downstream = NoopSubscriber.get[HttpData]()
    }

    override def completed(result: Integer, byteBuf: ByteBuf): Unit = {
      executor.execute(() => {
        if (closed) {
          byteBuf.release
          maybeCloseFileChannel()
        } else if (result >= 0) {
          position = position + result
          var data: HttpData = null
          if (withPooledObjects) {
            byteBuf.writerIndex(result)
            data = HttpData.wrap(byteBuf)
          } else {
            data = HttpData.wrap(ByteBufUtil.getBytes(byteBuf, 0, result))
            byteBuf.release
          }
          downstream.onNext(data)
          if (position < end) {
            reading = false
            read()
          } else {
            maybeCloseFileChannel()
            close0(None)
          }
        } else {
          byteBuf.release
          maybeCloseFileChannel()
          close0(None)
        }
      })
    }

    override def failed(ex: Throwable, byteBuf: ByteBuf): Unit = {
      executor.execute(() => {
        byteBuf.release
        maybeCloseFileChannel()
        close0(Some(ex))
      })
    }

    def maybeCloseFileChannel(): Unit = {
      if (fileChannel.isOpen) {
        try {
          fileChannel.close()
        } catch {
          case cause: IOException =>
            logger.warn("Unexpected exception while closing {}.", Array(fileChannel, cause): _*)
        }
      }
    }

    def close(cause: Option[Throwable]): Unit = {
      if (executor.inEventLoop) close0(cause)
      else executor.execute(() => close0(cause))
    }

    private def close0(cause: Option[Throwable]): Unit = {
      if (!closed) {
        closed = true
        cause match {
          case Some(ex) =>
            downstream.onError(ex)
            val _ = completionFuture.completeExceptionally(ex)
          case None =>
            downstream.onComplete()
            val _ = completionFuture.complete(null)
        }
      }
    }
  }
}
