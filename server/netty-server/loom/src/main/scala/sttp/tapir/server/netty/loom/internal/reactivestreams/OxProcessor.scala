package sttp.tapir.server.netty.loom.internal.reactivestreams

import org.reactivestreams.Subscriber
import ox.*
import ox.channels.*
import org.reactivestreams.Subscription
import org.reactivestreams.Processor
import sttp.tapir.server.netty.loom.internal.ox.OxDispatcher
import sttp.tapir.server.netty.loom.OxStreams

private[loom] class OxProcessor[A, B](oxDispatcher: OxDispatcher, wsPipe: OxStreams.Pipe[A, B]) extends Processor[A, B] {
  @volatile private var subscription: Subscription = _
  private val channel = Channel.buffered[A](1)

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
      s.request(1)
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

  override def subscribe(subscriber: Subscriber[? >: B]): Unit =
    if (subscriber == null) throw new NullPointerException("Subscriber cannot be null")
    val internalSource = (channel: Source[A])
    // Each time a request is read from the channel where we put it in onNext, we can ask for one more
    val transformation: Ox ?=> Source[A] => Source[A] = getAndRequest[A]
    val incomingRequests: Source[A] = oxDispatcher.transformSource[A, A](transformation, internalSource)
    // Applying the user-provided pipe
    val outgoingResponses: Source[B] = oxDispatcher.transformSource[A, B](wsPipe, incomingRequests)
    val subscription = new ChannelSubscription(oxDispatcher, subscriber, outgoingResponses, () => { val _ = channel.doneSafe() })
    subscriber.onSubscribe(subscription)

  def getAndRequest[B]: Ox ?=> Source[B] => Source[B] = { s =>
    s.map { e =>
      subscription.request(1)
      e
    }
  }
}
