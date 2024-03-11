package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}
import sttp.capabilities.StreamMaxLengthExceededException

import java.util.concurrent.LinkedBlockingQueue
import scala.concurrent.{Future, Promise}

private[netty] class SimpleSubscriber(contentLength: Option[Int]) extends PromisingSubscriber[Array[Byte], HttpContent] {
  private var subscription: Subscription = _
  private val resultPromise = Promise[Array[Byte]]()
  @volatile private var totalLength = 0
  private val resultBlockingQueue = new LinkedBlockingQueue[Either[Throwable, Array[Byte]]](1)
  @volatile private var buffers = Vector[ByteBuf]()

  override def future: Future[Array[Byte]] = resultPromise.future
  def resultBlocking(): Either[Throwable, Array[Byte]] = resultBlockingQueue.take()

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(1)
  }

  override def onNext(content: HttpContent): Unit = {
    val byteBuf = content.content()
    // If expected content length is known, and we receive exactly this amount of bytes, we assume there's only one chunk and
    // we can immediately return it without going through the buffer list.
    if (contentLength.contains(byteBuf.readableBytes())) {
      val finalArray = ByteBufUtil.getBytes(byteBuf)
      byteBuf.release()
      if (!resultBlockingQueue.offer(Right(finalArray))) {
        // Queue full, which is unexpected. The previous chunk was supposed the be the only one. A malformed request perhaps?
        subscription.cancel()
      } else {
        resultPromise.success(finalArray)
        subscription.request(1)
      }
    } else {
      buffers = buffers :+ byteBuf
      totalLength += byteBuf.readableBytes()
      subscription.request(1)
    }
  }

  override def onError(t: Throwable): Unit = {
    buffers.foreach { buf =>
      val _ = buf.release()
    }
    buffers = Vector.empty
    resultBlockingQueue.offer(Left(t))
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    if (!buffers.isEmpty) {
      val mergedArray = new Array[Byte](totalLength)
      var currentIndex = 0
      buffers.foreach { buf =>
        val length = buf.readableBytes()
        buf.getBytes(buf.readerIndex(), mergedArray, currentIndex, length)
        currentIndex += length
        val _ = buf.release()
      }
      buffers = Vector.empty
      if (!resultBlockingQueue.offer(Right(mergedArray))) {
        // Result queue full, which is unexpected.
        resultPromise.failure(new IllegalStateException("Calling onComplete after result was already returned"))
      } else
        resultPromise.success(mergedArray)
    } else {
      () // result already sent in onNext
    }
  }

}

object SimpleSubscriber {

  def processAll(publisher: Publisher[HttpContent], contentLength: Option[Int], maxBytes: Option[Long]): Future[Array[Byte]] =
    maxBytes match {
      case Some(max) if (contentLength.exists(_ > max)) => Future.failed(StreamMaxLengthExceededException(max))
      case Some(max) => {
        val subscriber = new SimpleSubscriber(contentLength)
        publisher.subscribe(new LimitedLengthSubscriber(max, subscriber))
        subscriber.future
      }
      case None => {
        val subscriber = new SimpleSubscriber(contentLength)
        publisher.subscribe(subscriber)
        subscriber.future
      }
    }

  def processAllBlocking(publisher: Publisher[HttpContent], contentLength: Option[Int], maxBytes: Option[Long]): Array[Byte] =
    maxBytes match {
      case Some(max) if (contentLength.exists(_ > max)) => throw new StreamMaxLengthExceededException(max)
      case Some(max) => {
        val subscriber = new SimpleSubscriber(contentLength)
        publisher.subscribe(new LimitedLengthSubscriber(max, subscriber))
        subscriber.resultBlocking() match {
          case Right(result) => result
          case Left(e)       => throw e
        }
      }
      case None => {
        val subscriber = new SimpleSubscriber(contentLength)
        publisher.subscribe(subscriber)
        subscriber.resultBlocking() match {
          case Right(result) => result
          case Left(e)       => throw e
        }
      }
    }
}
