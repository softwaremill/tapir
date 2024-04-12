package sttp.tapir.server.netty.loom.reactivestreams

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import ox.*
import ox.channels.*

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

class OxPublisher[A](source: Source[A], closeCh: () => Unit)(using Ox) extends Publisher[A] {
  override def subscribe(subscriber: Subscriber[? >: A]): Unit =
    subscriber.onSubscribe(new ChannelSubscription(subscriber, source, closeCh))
}

class ChannelSubscription[A](subscriber: Subscriber[? >: A], source: Source[A], closeCh: () => Unit)(using Ox) extends Subscription {
  private val demand: AtomicLong = AtomicLong(0L)
  private val isCompleted = new AtomicBoolean(false)
  private val readingInProgress = new AtomicBoolean(false)

  override def cancel(): Unit =
    isCompleted.set(true)
    closeCh()

  override def request(n: Long): Unit =
    if (n <= 0) subscriber.onError(new IllegalArgumentException("§3.9: n must be greater than 0"))
    else {
      demand.addAndGet(n)
      readNext()
    }

  def readNext(): Unit =
    if (demand.get() > 0 && !isCompleted.get() && readingInProgress.compareAndSet(false, true)) {
      fork {
        var chunkDemand: Long = demand.getAndSet(0L)
        while (chunkDemand > 0) {
          source.receiveSafe() match {
            case elem: A =>
              chunkDemand -= 1
              subscriber.onNext(elem)
            case ChannelClosed.Done =>
              isCompleted.set(true)
              subscriber.onComplete()
            case ChannelClosed.Error(e) =>
              isCompleted.set(true)
              subscriber.onError(e)
          }
        }
        readingInProgress.set(false)
        // can start a fork from within this current fork, but this is OK, the "parent" fork will finish while the "child" fork
        // will run normally
        readNext()
      }
    }
}
