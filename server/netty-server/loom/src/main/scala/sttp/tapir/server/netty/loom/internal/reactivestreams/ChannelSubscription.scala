package sttp.tapir.server.netty.loom.internal.reactivestreams

import org.reactivestreams.{Subscriber, Subscription}
import ox.*
import ox.channels.*
import sttp.tapir.server.netty.loom.internal.ox.OxDispatcher

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

private[loom] class ChannelSubscription[A](
    oxDispatcher: OxDispatcher,
    subscriber: Subscriber[? >: A],
    source: Source[A],
    closeCh: () => Unit
) extends Subscription {
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
      oxDispatcher.runAsync(() => {
        var chunkDemand: Long = demand.getAndSet(0L)
        while (chunkDemand > 0 && !isCompleted.get()) {
          source.receiveSafe() match {
            case ChannelClosed.Done =>
              isCompleted.set(true)
              subscriber.onComplete()
            case ChannelClosed.Error(e) =>
              isCompleted.set(true)
              subscriber.onError(e)
            case elem: A @unchecked =>
              chunkDemand -= 1
              subscriber.onNext(elem)
          }
        }
        readingInProgress.set(false)
        // can start a fork from within this current fork, but this is OK, the "parent" fork will finish while the "child" fork
        // will run normally
        readNext()
      })
    }
}
