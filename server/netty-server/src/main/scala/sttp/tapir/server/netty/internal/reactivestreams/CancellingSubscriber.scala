package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Subscriber, Subscription}

/** A subscriber which immediately cancels the subscription. Used in combination with [[SubscribeTrackingStreamedHttpRequest]] to safely
  * discard the request body, in case it was never subscribed to.
  */
class CancellingSubscriber extends Subscriber[HttpContent] {
  override def onComplete(): Unit = ()
  override def onError(t: Throwable): Unit = ()
  override def onNext(t: HttpContent): Unit = ()
  override def onSubscribe(s: Subscription): Unit = s.cancel()
}
