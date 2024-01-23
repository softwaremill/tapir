package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}

import java.util.concurrent.ConcurrentLinkedQueue
import scala.collection.JavaConverters._
import scala.concurrent.{Future, Promise}
import java.util.concurrent.LinkedBlockingQueue

private[netty] class SimpleSubscriber() extends PromisingSubscriber[Array[Byte], HttpContent] {
  private var subscription: Subscription = _
  private val chunks = new ConcurrentLinkedQueue[Array[Byte]]()
  private var size = 0
  private val resultPromise = Promise[Array[Byte]]()
  private val resultBlockingQueue = new LinkedBlockingQueue[Either[Throwable, Array[Byte]]]()

  override def future: Future[Array[Byte]] = resultPromise.future
  def resultBlocking(): Either[Throwable, Array[Byte]] = resultBlockingQueue.take()

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    s.request(1)
  }

  override def onNext(content: HttpContent): Unit = {
    val array = ByteBufUtil.getBytes(content.content())
    content.release()
    size += array.length
    chunks.add(array)
    subscription.request(1)
  }

  override def onError(t: Throwable): Unit = {
    chunks.clear()
    resultBlockingQueue.add(Left(t))
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    val result = new Array[Byte](size)
    val _ = chunks.asScala.foldLeft(0)((currentPosition, array) => {
      System.arraycopy(array, 0, result, currentPosition, array.length)
      currentPosition + array.length
    })
    chunks.clear()
    resultBlockingQueue.add(Right(result))
    resultPromise.success(result)
  }
}

object SimpleSubscriber {
  def processAll(publisher: Publisher[HttpContent], maxBytes: Option[Long]): Future[Array[Byte]] = {
    val subscriber = new SimpleSubscriber()
    publisher.subscribe(maxBytes.map(max => new LimitedLengthSubscriber(max, subscriber)).getOrElse(subscriber))
    subscriber.future
  }

  def processAllBlocking(publisher: Publisher[HttpContent], maxBytes: Option[Long]): Array[Byte] = {
    val subscriber = new SimpleSubscriber()
    publisher.subscribe(maxBytes.map(max => new LimitedLengthSubscriber(max, subscriber)).getOrElse(subscriber))
    subscriber.resultBlocking() match {
      case Right(result) => result
      case Left(e)       => throw e
    }
  }
}
