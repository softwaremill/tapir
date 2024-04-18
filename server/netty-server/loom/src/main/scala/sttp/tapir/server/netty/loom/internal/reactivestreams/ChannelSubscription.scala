package sttp.tapir.server.netty.loom.internal.reactivestreams

import org.reactivestreams.{Subscriber, Subscription}
import ox.*
import ox.channels.*

private[loom] class ChannelSubscription[A](
    subscriber: Subscriber[? >: A],
    source: Source[A]
) extends Subscription {
  private val demands: Channel[Long] = Channel.unlimited[Long]

  def runBlocking() =
    demands.foreach { demand =>
      var i = 0L
      while (i < demand) {
        source.receiveOrClosed() match {
          case ChannelClosed.Done =>
            demands.doneOrClosed().discard
            i = demand // break early
            subscriber.onComplete()
          case ChannelClosed.Error(e) =>
            demands.doneOrClosed().discard
            i = demand
            subscriber.onError(e)
          case elem: A @unchecked =>
            i = i + 1
            subscriber.onNext(elem)
        }
      }
    }

  override def cancel(): Unit =
    demands.doneOrClosed().discard

  override def request(n: Long): Unit =
    if (n <= 0) subscriber.onError(new IllegalArgumentException("§3.9: n must be greater than 0"))
    else {
      demands.send(n)
    }
}
