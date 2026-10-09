package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscriber, Subscription}

/** Publishes the bytes of each chunk, releasing the chunk immediately. Use when the chunks might be buffered downstream, and lost without
  * being released if the stream fails (#4194, #4169).
  */
private[netty] class CopyingPublisher(underlying: Publisher[HttpContent]) extends Publisher[Array[Byte]] {
  override def subscribe(s: Subscriber[? >: Array[Byte]]): Unit =
    underlying.subscribe(new Subscriber[HttpContent] {
      override def onSubscribe(subscription: Subscription): Unit = s.onSubscribe(subscription)
      override def onNext(content: HttpContent): Unit =
        s.onNext(
          try ByteBufUtil.getBytes(content.content())
          finally { val _ = content.release() }
        )
      override def onError(t: Throwable): Unit = s.onError(t)
      override def onComplete(): Unit = s.onComplete()
    })
}
