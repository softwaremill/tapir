package sttp.tapir.server.netty.sync.internal.reactivestreams

import org.reactivestreams.{Processor, Subscriber, Subscription}
import org.slf4j.LoggerFactory
import ox.*
import ox.channels.*
import sttp.tapir.server.netty.sync.OxStreams
import sttp.tapir.server.netty.sync.internal.ox.OxDispatcher

import scala.concurrent.duration.*
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

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
private[sync] class OxProcessor[A, B](
    oxDispatcher: OxDispatcher,
    pipeline: OxStreams.Pipe[A, B],
    wrapSubscriber: Subscriber[? >: B] => Subscriber[? >: B]
) extends Processor[A, B]:
  private val logger = LoggerFactory.getLogger(getClass.getName)
  // Incoming requests are read from this subscription into an Ox Channel[A]
  @volatile private var requestsSubscription: Subscription = _
  // An internal channel for holding incoming requests (`A`), will be wrapped with user's pipeline to produce responses (`B`)
  private val channel = Channel.buffered[A](1)

  private val pipelineCancelationTimeout = 5.seconds
  @volatile private var pipelineForkFuture: Future[CancellableFork[Unit]] = _

  override def onError(reason: Throwable): Unit =
    // As per rule 2.13, we need to throw a `java.lang.NullPointerException` if the `Throwable` is `null`
    if reason == null then throw null
    channel.errorOrClosed(reason).discard
    cancelPipelineFork()

  override def onNext(a: A): Unit =
    if a == null then throw new NullPointerException("Element cannot be null") // Rule 2.13
    else
      channel.sendOrClosed(a) match
        case () => ()
        case _: ChannelClosed =>
          cancelSubscription()
          onError(new IllegalStateException("onNext called when the channel is closed"))

  override def onSubscribe(s: Subscription): Unit =
    if s == null then throw new NullPointerException("Subscription cannot be null")
    else if requestsSubscription != null then s.cancel() // Rule 2.5: if onSubscribe is called twice, must cancel the second subscription
    else
      requestsSubscription = s
      s.request(1)

  override def onComplete(): Unit =
    channel.doneOrClosed().discard
    cancelPipelineFork()

  override def subscribe(subscriber: Subscriber[? >: B]): Unit =
    if subscriber == null then throw new NullPointerException("Subscriber cannot be null")
    val wrappedSubscriber = wrapSubscriber(subscriber)
    pipelineForkFuture = oxDispatcher.runAsync {
      val outgoingResponses: Source[B] = pipeline((channel: Source[A]).mapAsView { e =>
        requestsSubscription.request(1)
        e
      })
      val channelSubscription = new ChannelSubscription(wrappedSubscriber, outgoingResponses)
      subscriber.onSubscribe(channelSubscription)
      channelSubscription.runBlocking() // run the main loop which reads from the channel if there's demand
    } { error =>
      wrappedSubscriber.onError(error)
      onError(error)
    }

  private def cancelPipelineFork(): Unit =
    if (pipelineForkFuture != null) try {
      val pipelineFork = Await.result(pipelineForkFuture, pipelineCancelationTimeout)
      oxDispatcher.runAsync {
        race(
          {
            ox.sleep(pipelineCancelationTimeout)
            logger.error(s"Pipeline fork cancelation did not complete in time ($pipelineCancelationTimeout).")
          },
          pipelineFork.cancel()
        ) match {
          case Left(NonFatal(e)) => logger.error("Error when canceling pipeline fork", e)
          case _                 => ()
        }
      } { e => logger.error("Error when canceling pipeline fork", e) }.discard
    } catch case NonFatal(e) => logger.error("Error when waiting for pipeline fork to start", e)

  private def cancelSubscription() =
    if requestsSubscription != null then
      try requestsSubscription.cancel()
      catch
        case t: Throwable =>
          throw new IllegalStateException(
            s"$requestsSubscription violated the Reactive Streams rule 3.15 by throwing an exception from cancel.",
            t
          )
