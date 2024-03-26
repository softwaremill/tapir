package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}
import sttp.capabilities.StreamMaxLengthExceededException

import java.util.concurrent.LinkedBlockingQueue
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

private[netty] class SimpleSubscriber(contentLength: Option[Long]) extends PromisingSubscriber[Array[Byte], HttpContent] {
  // These don't need to be volatile as Reactive Streams guarantees that onSubscribe/onNext/onError/onComplete are
  // called serially (https://github.com/reactive-streams/reactive-streams-jvm?tab=readme-ov-file#1-publisher-code - rule 3)
  private var subscription: Subscription = _
  private var buffers = Vector[ByteBuf]()
  private var totalLength = 0

  private val resultPromise = Promise[Array[Byte]]()

  override def future: Future[Array[Byte]] = resultPromise.future

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(1)
  }

  override def onNext(content: HttpContent): Unit = {
    val byteBuf = content.content()
    // If expected content length is known, we haven't received any data yet, and we receive exactly this amount of bytes,
    // we assume there's only one chunk and we can immediately return it without going through the buffer list.
    if (buffers.isEmpty && contentLength.contains(byteBuf.readableBytes())) {
      val finalArray = ByteBufUtil.getBytes(byteBuf)
      byteBuf.release()
      if (!resultPromise.trySuccess(finalArray)) {
        // Result is set, which is unexpected. The previous chunk was supposed the be the only one.
        // A malformed request perhaps?
        subscription.cancel()
      } else {
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
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    if (buffers.nonEmpty) {
      val mergedArray = new Array[Byte](totalLength)
      var currentIndex = 0
      buffers.foreach { buf =>
        val length = buf.readableBytes()
        buf.getBytes(buf.readerIndex(), mergedArray, currentIndex, length)
        currentIndex += length
        val _ = buf.release()
      }
      buffers = Vector.empty
      resultPromise.success(mergedArray)
    } else {
      () // result already sent in onNext
    }
  }

}

object SimpleSubscriber {

  def processAll(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): Future[Array[Byte]] =
    maxBytes match {
      case Some(max) if contentLength.exists(_ > max) =>
        Future.failed(StreamMaxLengthExceededException(max))
      case _ =>
        val subscriber = new SimpleSubscriber(contentLength)
        val maybeLimitedSubscriber = maxBytes.map(new LimitedLengthSubscriber(_, subscriber)).getOrElse(subscriber)
        publisher.subscribe(maybeLimitedSubscriber)
        subscriber.future
    }

  def processAllBlocking(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): Array[Byte] =
    Await.result(processAll(publisher, contentLength, maxBytes), Duration.Inf)
}
