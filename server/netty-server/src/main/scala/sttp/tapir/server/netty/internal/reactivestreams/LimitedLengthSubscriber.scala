package sttp.tapir.server.netty.internal.reactivestreams

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.JavaConverters._
import scala.concurrent.Promise
import io.netty.handler.codec.http.HttpContent
import scala.concurrent.Future
import io.netty.buffer.ByteBufUtil
import sttp.capabilities.StreamMaxLengthExceededException

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
// Requests all data at once and loads it into memory

private[netty] class LimitedLengthSubscriber[R](maxBytes: Long, delegate: PromisingSubscriber[R, HttpContent])
    extends PromisingSubscriber[R, HttpContent] {
  private var size = 0L

  override def future: Future[R] = delegate.future

  override def onSubscribe(s: Subscription): Unit =
    delegate.onSubscribe(s)

  override def onNext(content: HttpContent): Unit = {
    assert(content != null)
    size = size + content.content.readableBytes()
    if (size > maxBytes)
      onError(StreamMaxLengthExceededException(maxBytes))
    else
      delegate.onNext(content)
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    delegate.onError(t)
  }

  override def onComplete(): Unit = {
    delegate.onComplete()
  }
}

object LimitedLengthSubscriber {
  def ()
}
