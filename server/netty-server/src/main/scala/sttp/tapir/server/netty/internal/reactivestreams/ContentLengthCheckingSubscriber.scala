package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Subscriber, Subscription}
import sttp.tapir.server.model.IncompleteRequestBodyException

/** Fails the stream with an [[IncompleteRequestBodyException]] if it completes before `contentLength` bytes are received. When the
  * connection is closed mid-body, Netty completes the body publisher normally, so without this check a truncated body would be
  * indistinguishable from a complete one (#4169).
  */
private[netty] class ContentLengthCheckingSubscriber(contentLength: Long, delegate: Subscriber[? >: HttpContent])
    extends Subscriber[HttpContent] {
  // doesn't need to be volatile, as Reactive Streams guarantees that the signals are serial
  private var bytesReceived = 0L

  override def onSubscribe(s: Subscription): Unit = delegate.onSubscribe(s)

  override def onNext(content: HttpContent): Unit = {
    bytesReceived += content.content().readableBytes()
    delegate.onNext(content)
  }

  override def onError(t: Throwable): Unit = delegate.onError(t)

  override def onComplete(): Unit =
    if (bytesReceived < contentLength) delegate.onError(IncompleteRequestBodyException(bytesReceived, contentLength))
    else delegate.onComplete()
}
