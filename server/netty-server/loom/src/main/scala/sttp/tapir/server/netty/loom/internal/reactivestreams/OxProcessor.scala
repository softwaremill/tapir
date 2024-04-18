package sttp.tapir.server.netty.loom.internal.reactivestreams

import org.reactivestreams.Subscriber
import ox.*
import ox.channels.*
import org.reactivestreams.Subscription
import org.reactivestreams.Processor
import sttp.tapir.server.netty.loom.internal.ox.OxDispatcher
import sttp.tapir.server.netty.loom.OxStreams

/** A reactive Processor, which is both a Publisher and a Subscriber
  *
  * @param oxDispatcher
  *   a dispatcher to which async tasks can be submitted (reading from a channel)
  * @param pipeline
  *   user-defined processing pipeline expressed as an Ox Source => Source transformation
  * @param wrapSubscriber
  *   an optional function allowing wrapping external subscribers, can be used to intercept onNext, onComplete and onError with custom
  *   handling. Can be just identity.
  */
private[loom] class OxProcessor[A, B](
    oxDispatcher: OxDispatcher,
    pipeline: OxStreams.Pipe[A, B],
    wrapSubscriber: Subscriber[? >: B] => Subscriber[? >: B]
) extends Processor[A, B] {
  @volatile private var subscription: Subscription = _
  private val channel = Channel.buffered[A](1)

  override def onError(reason: Throwable): Unit =
    // As per rule 2.13, we need to throw a `java.lang.NullPointerException` if the `Throwable` is `null`
    if (reason == null) throw null
    channel.errorOrClosed(reason).discard

  override def onNext(a: A): Unit =
    if (a == null) {
      throw new NullPointerException("Element cannot be null") // Rule 2.13
    } else {
      channel.sendOrClosed(a) match {
        case () => ()
        case _: ChannelClosed =>
          cancelSubscription()
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
    channel.doneOrClosed().discard

  override def subscribe(subscriber: Subscriber[? >: B]): Unit =
    if (subscriber == null) throw new NullPointerException("Subscriber cannot be null")
    val wrappedSubscriber = wrapSubscriber(subscriber)
    oxDispatcher.runAsync {
      supervised {
        val outgoingResponses: Source[B] = pipeline((channel: Source[A]).map { e =>
          subscription.request(1); e
        })
        val channelSubscription = new ChannelSubscription(wrappedSubscriber, outgoingResponses)
        subscriber.onSubscribe(channelSubscription)
        channelSubscription.runBlocking()
      }
    } { error =>
      wrappedSubscriber.onError(error)
      onError(error)
    }

  private def cancelSubscription() =
    if (subscription != null)
      try {
        subscription.cancel()
      } catch {
        case t: Throwable => {
          (new IllegalStateException(
            s"$subscription violated the Reactive Streams rule 3.15 by throwing an exception from cancel.",
            t
          ))
            .printStackTrace(System.err)
        }
      }
}
