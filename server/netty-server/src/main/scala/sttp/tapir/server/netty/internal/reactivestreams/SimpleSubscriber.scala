package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}

private[netty] class SimpleSubscriber() extends PromisingSubscriber[Array[Byte], HttpContent] {
  private var subscription: Subscription = _
  private val chunks = new ConcurrentLinkedQueue[Array[Byte]]()
  private var size = 0
  private val resultPromise = Promise[Array[Byte]]()

  override def future: Future[Array[Byte]] = resultPromise.future

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(1)
  }

  override def onNext(content: HttpContent): Unit = {
    val a = ByteBufUtil.getBytes(content.content())
    size += a.length
    chunks.add(a)
    subscription.request(1)
  }

  override def onError(t: Throwable): Unit = {
    chunks.clear()
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    val result = new Array[Byte](size)
    chunks.asScala.foldLeft(0)((currentPosition, array) => {
      System.arraycopy(array, 0, result, currentPosition, array.length)
      currentPosition + array.length
    })
    chunks.clear()
    resultPromise.success(result)
  }
}

object SimpleSubscriber {
  def processAll(publisher: Publisher[HttpContent], maxBytes: Option[Long]): Future[Array[Byte]] = {
    val subscriber = new SimpleSubscriber()
    publisher.subscribe(maxBytes.map(max => new LimitedLengthSubscriber(max, subscriber)).getOrElse(subscriber))
    subscriber.future
  }
}
