package sttp.tapir.server.netty.loom.internal.reactivestreams

import org.reactivestreams.Subscriber
import ox.*
import ox.channels.*
import org.reactivestreams.Subscription
import org.reactivestreams.Processor
import sttp.tapir.server.netty.loom.internal.ox.OxDispatcher
import sttp.tapir.server.netty.loom.OxStreams

private[loom] class OxProcessor[A, B](a: ActorRef[OxDispatcher], wsPipe: OxStreams.Pipe[A, B]) extends Processor[A, B] {
  @volatile private var subscription: Subscription = _
  private val channel = Channel.buffered[A](1)

  override def onError(reason: Throwable): Unit =
    // As per rule 2.13, we need to throw a `java.lang.NullPointerException` if the `Throwable` is `null`
    println("OnError!")
    if (reason == null) throw null
    val _ = channel.errorSafe(reason)

  override def onNext(a: A): Unit =
    if (a == null) {
      throw new NullPointerException("Element cannot be null") // Rule 2.13
    } else {
      // println("onNext called, putting received element in the channel")
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
      // println(s"onSubscribe called in the processor, requesting $demand elements")
      subscription = s
      s.request(1)
    }

  override def onComplete(): Unit =
    println("onComplete!")
    val _ = channel.doneSafe()

  def done(): Unit =
    println("done!")
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
    // println("Processor.subscribe called")
    if (subscriber == null) throw new NullPointerException("Subscriber cannot be null")
    val internalSource = (channel: Source[A])
    // println("Internal source is now running, shoving it into the wrapper pipe")
    // Each time a request is read from the channel where we put it in onNext, we can ask for one more
    val transformation: Ox ?=> Source[A] => Source[A] = getAndRequest[A]
    val incomingRequests: Source[A] = a.ask(_.transformSource[A, A](transformation, internalSource))
    // Applying the user-provided pipe
    val outgoingResponses: Source[B] = a.ask(_.transformSource[A, B](wsPipe, incomingRequests))
    // println("Source prepared")
    val subscription = new ChannelSubscription(a, subscriber, outgoingResponses, () => { val _ = channel.doneSafe() })
    // println("Subscription prepared, subscribing")
    subscriber.onSubscribe(subscription)
    // println("Publisher is now subscribed")

  def getAndRequest[B]: Ox ?=> Source[B] => Source[B] = { s =>
    s.map { e =>
      // println(s"Element flowing through source: $e")
      subscription.request(1)
      e
    }
  }
}
