package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Subscriber, Subscription}
import sttp.capabilities.StreamMaxLengthExceededException

import scala.collection.JavaConverters._

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
private[netty] class LimitedLengthSubscriber[R](maxBytes: Long, delegate: Subscriber[HttpContent]) extends Subscriber[HttpContent] {
  private var subscription: Subscription = _
  private var bytesReadSoFar = 0L

  override def onSubscribe(s: Subscription): Unit = {
    subscription = s
    delegate.onSubscribe(s)
  }

  override def onNext(content: HttpContent): Unit = {
    bytesReadSoFar = bytesReadSoFar + content.content.readableBytes()
    if (bytesReadSoFar > maxBytes) {
      subscription.cancel()
      onError(StreamMaxLengthExceededException(maxBytes))
      subscription = null
    } else
      delegate.onNext(content)
  }

  override def onError(t: Throwable): Unit = {
    if (subscription != null)
      delegate.onError(t)
  }

  override def onComplete(): Unit = {
    if (subscription != null)
      delegate.onComplete()
  }
}
