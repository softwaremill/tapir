package sttp.tapir.server.netty.sync.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.util.{Collections, IdentityHashMap}

/** Publishes the body chunks of `underlying`, keeping track of the ones not yet taken over by the consumer. When the consumer stops early
  * (e.g. because of an error), [[releaseUnconsumed]] releases the chunks that remain buffered downstream - ox's channels drop buffered
  * elements on errors.
  */
private[sync] class UnconsumedContentTracker(underlying: Publisher[HttpContent]) extends Publisher[HttpContent]:
  // guarded by `unconsumed`
  private val unconsumed = Collections.newSetFromMap(new IdentityHashMap[HttpContent, java.lang.Boolean]())
  private var closed = false

  override def subscribe(s: Subscriber[? >: HttpContent]): Unit =
    underlying.subscribe(new Subscriber[HttpContent]:
      override def onSubscribe(subscription: Subscription): Unit = s.onSubscribe(subscription)
      override def onNext(content: HttpContent): Unit =
        val tracked = unconsumed.synchronized { !closed && unconsumed.add(content) }
        if tracked then s.onNext(content) else content.release(): Unit
      override def onError(t: Throwable): Unit = s.onError(t)
      override def onComplete(): Unit = s.onComplete()
    )

  /** Marks `content` as owned by the consumer, which becomes responsible for releasing it. */
  def consumed(content: HttpContent): HttpContent =
    unconsumed.synchronized { unconsumed.remove(content) }: Unit
    content

  /** Releases all chunks not yet consumed; chunks arriving afterwards are released immediately. */
  def releaseUnconsumed(): Unit = unconsumed.synchronized {
    closed = true
    unconsumed.forEach(c => c.release(): Unit)
    unconsumed.clear()
  }
