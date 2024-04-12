package sttp.tapir.server.netty.loom.reactivestreams

import org.reactivestreams.{Subscriber, Subscription}
import ox.*
import ox.channels.*

class OxSubscriber[A](using ox: Ox) extends Subscriber[A] {
  @volatile private var subscription: Subscription = _
  private val bufferSize = 1
  private var demand = bufferSize
  private val channel = Channel.buffered[A](bufferSize)

  override def onError(reason: Throwable): Unit =
    // As per rule 2.13, we need to throw a `java.lang.NullPointerException` if the `Throwable` is `null`
    if (reason == null) throw null
    val _ = channel.errorSafe(reason)

  override def onNext(a: A): Unit =
    if (a == null) {
      throw new NullPointerException("Element cannot be null") // Rule 2.13
    } else {
      channel.sendSafe(a) match {
        case () => ()
        case _: ChannelClosed => // Rule 2.13
          done()
          onError(new IllegalStateException("onNext called when the channel is closed"))
      }
    }

  override def onSubscribe(s: Subscription): Unit =
    if (s == null) {
      throw new NullPointerException("Subscription cannot be null")
    } else if (subscription != null) {
      s.cancel() // Rule 2.5: if onSubscribe is called twice, must cancel the second subscription
    } else {
      subscription = s
      if (demand > 0) { // TODO not sure if we need demand or just bufferSize
        s.request(demand) // Rule 2.1: Subscriber must signal demand via Subscription.request(long)
        demand = 0
      }
    }

  override def onComplete(): Unit =
    val _ = channel.doneSafe()

  def done(): Unit =
    val _ = channel.doneSafe()
    if (subscription != null)
      try {
        subscription.cancel()
      } catch {
        case t: Throwable => {
          (new IllegalStateException(s"$subscription violated the Reactive Streams rule 3.15 by throwing an exception from cancel.", t))
            .printStackTrace(System.err)
        }
      }

  def source(): Source[A] = 
    (channel: Source[A]).map { elem => 
    subscription.request(1)
    elem 
  }(using ox, StageCapacity(1))
}
