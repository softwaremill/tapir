package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sttp.capabilities.StreamMaxLengthExceededException

import java.io.{IOException, InputStream}
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import scala.annotation.tailrec
import scala.concurrent.Promise

/** A blocking input stream that reads from a reactive streams publisher of [[HttpContent]].
  * @param maxBufferedChunks
  *   maximum number of unread chunks that can be buffered before blocking the publisher
  */
private[netty] class SubscriberInputStream(maxBufferedChunks: Int = 1) extends InputStream with Subscriber[HttpContent] {

  require(maxBufferedChunks > 0)

  import SubscriberInputStream._

  // volatile because used in both InputStream & Subscriber methods
  @volatile private[this] var closed = false

  // Calls on the subscription must be synchronized in order to satisfy the Reactive Streams spec
  // (https://github.com/reactive-streams/reactive-streams-jvm?tab=readme-ov-file#2-subscriber-code - rule 7)
  // because they are called both from InputStream & Subscriber methods.
  private[this] var subscription: Subscription = _
  private[this] val lock = new ReentrantLock

  private def locked[T](code: => T): T =
    try {
      lock.lock()
      code
    } finally {
      lock.unlock()
    }

  private[this] var currentItem: Item = _
  // the queue serves as a buffer to allow for possible parallelism between the subscriber and the publisher
  private val queue = new LinkedBlockingQueue[Item](maxBufferedChunks + 1) // +1 to have a spot for End/Error

  private def readItem(blocking: Boolean): Item = {
    if (currentItem eq null) {
      currentItem = if (blocking) queue.take() else queue.poll()
      currentItem match {
        case _: Chunk => locked(subscription.request(1))
        case _        =>
      }
    }
    currentItem
  }

  override def available(): Int =
    readItem(blocking = false) match {
      case Chunk(data) => data.readableBytes()
      case _           => 0
    }

  override def read(): Int = {
    val buffer = new Array[Byte](1)
    if (read(buffer) == -1) -1 else buffer(0)
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int =
    if (closed) throw new IOException("Stream closed")
    else if (len == 0) 0
    else
      readItem(blocking = true) match {
        case Chunk(data) =>
          val toRead = Math.min(len, data.readableBytes())
          data.readBytes(b, off, toRead)
          if (data.readableBytes() == 0) {
            data.release()
            currentItem = null
          }
          toRead
        case Error(cause) => throw cause
        case End          => -1
      }

  override def close(): Unit = if (!closed) {
    locked(subscription.cancel())
    closed = true
    clearQueue()
  }

  @tailrec private def clearQueue(): Unit =
    queue.poll() match {
      case Chunk(data) =>
        data.release()
        clearQueue()
      case _ =>
    }

  override def onSubscribe(s: Subscription): Unit = locked {
    if (s eq null) {
      throw new NullPointerException("Subscription must not be null")
    }
    subscription = s
    subscription.request(maxBufferedChunks)
  }

  override def onNext(chunk: HttpContent): Unit = {
    if (!queue.offer(Chunk(chunk.content()))) {
      // This should be impossible according to the Reactive Streams spec,
      // if it happens then it's a bug in the implementation of the subscriber of publisher
      chunk.release()
      locked(subscription.cancel())
    } else if (closed) {
      clearQueue()
    }
  }

  override def onError(t: Throwable): Unit =
    if (!closed) {
      queue.offer(Error(t))
    }

  override def onComplete(): Unit =
    if (!closed) {
      queue.offer(End)
    }
}
private[netty] object SubscriberInputStream {
  private sealed abstract class Item
  private case class Chunk(data: ByteBuf) extends Item
  private case class Error(cause: Throwable) extends Item
  private object End extends Item

  def processAsStream(
      publisher: Publisher[HttpContent],
      contentLength: Option[Long],
      maxBytes: Option[Long],
      maxBufferedChunks: Int = 1
  ): InputStream = maxBytes match {
    case Some(max) if contentLength.exists(_ > max) =>
      throw StreamMaxLengthExceededException(max)
    case _ =>
      val subscriber = new SubscriberInputStream(maxBufferedChunks)
      val maybeLimitedSubscriber = maxBytes.map(new LimitedLengthSubscriber(_, subscriber)).getOrElse(subscriber)
      publisher.subscribe(maybeLimitedSubscriber)
      subscriber
  }
}
