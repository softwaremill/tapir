package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import java.util.concurrent.LinkedBlockingQueue
import sttp.capabilities.StreamMaxLengthExceededException
import io.netty.buffer.Unpooled
import io.netty.buffer.ByteBuf
import io.netty.buffer.CompositeByteBuf
import scala.collection.mutable

private[netty] class SimpleSubscriber(contentLength: Option[Int]) extends PromisingSubscriber[Array[Byte], HttpContent] {
  private var subscription: Subscription = _
  private val resultPromise = Promise[Array[Byte]]()
  private var totalLength = 0
  private val resultBlockingQueue = new LinkedBlockingQueue[Either[Throwable, Array[Byte]]]()
  private val buffers = new ConcurrentLinkedQueue[ByteBuf]()

  override def future: Future[Array[Byte]] = resultPromise.future
  def resultBlocking(): Either[Throwable, Array[Byte]] = resultBlockingQueue.take()

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(1)
  }

  override def onNext(content: HttpContent): Unit = {
    val byteBuf = content.content()
    if (contentLength.exists(_ == byteBuf.readableBytes())) {
      val finalArray = ByteBufUtil.getBytes(byteBuf)
      byteBuf.release()
      resultBlockingQueue.add(Right(finalArray))
      resultPromise.success(finalArray)
    } else {
      buffers.add(byteBuf)
      totalLength += byteBuf.readableBytes()
    }
    subscription.request(1)
  }

  override def onError(t: Throwable): Unit = {
    buffers.forEach { buf =>
      val _ = buf.release()
    }
    buffers.clear()
    resultBlockingQueue.add(Left(t))
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    if (!buffers.isEmpty()) {
      val mergedArray = new Array[Byte](totalLength)
      var currentIndex = 0
      buffers.forEach { buf =>
        val length = buf.readableBytes()
        buf.getBytes(buf.readerIndex(), mergedArray, currentIndex, length)
        currentIndex += length
        val _ = buf.release()
      }
      resultBlockingQueue.add(Right(mergedArray))
      resultPromise.success(mergedArray)
      buffers.clear()
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
