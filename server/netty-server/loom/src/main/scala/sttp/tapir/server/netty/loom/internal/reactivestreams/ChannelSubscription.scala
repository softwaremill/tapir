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
      for (_ <- 0L until demand) {
        source.receiveOrClosed() match {
          case ChannelClosed.Done =>
            demands.doneOrClosed().discard
            subscriber.onComplete()
          case ChannelClosed.Error(e) =>
            demands.doneOrClosed().discard
            subscriber.onError(e)
          case elem: A @unchecked =>
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
