package sttp.tapir.server.netty.internal.reactivestreams

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.JavaConverters._
import scala.concurrent.Promise
import io.netty.handler.codec.http.HttpContent
import scala.concurrent.Future
import io.netty.buffer.ByteBufUtil

// based on org.asynchttpclient.request.body.generator.ReactiveStreamsBodyGenerator.SimpleSubscriber
// Requests all data at once and loads it into memory

private[netty] class SimpleSubscriber() extends PromisingSubscriber[ByteBuffer, HttpContent] {
  // a pair of values: (is cancelled, current subscription)
  private val subscription = new AtomicReference[(Boolean, Subscription)]((false, null))
  private val chunks = new ConcurrentLinkedQueue[Array[Byte]]()
  private var size = 0
  private val resultPromise = Promise[ByteBuffer]()

  override def future: Future[ByteBuffer] = resultPromise.future

  override def onSubscribe(s: Subscription): Unit = {
    assert(s != null)

    // The following can be safely run multiple times, as cancel() is idempotent
    val result = subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        // If someone has made a mistake and added this Subscriber multiple times, let's handle it gracefully
        if (current._2 != null) {
          current._2.cancel() // Cancel the additional subscription
        }

        if (current._1) { // already cancelled
          s.cancel()
          (true, null)
        } else { // happy path
          (false, s)
        }
      }
    })

    if (result._2 != null) {
      result._2.request(Long.MaxValue) // not cancelled, we can request data
    }
  }

  override def onNext(content: HttpContent): Unit = {
    assert(content != null)
    println(content.content().readableBytes())
    println("On next, calling getBytes")
    val a = ByteBufUtil.getBytes(content.content())
    println("Bytes loaded")
    size += a.length
    chunks.add(a)
  }

  override def onError(t: Throwable): Unit = {
    assert(t != null)
    chunks.clear()
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    println(">>>>>> onComplete")
    val result = ByteBuffer.allocate(size)
    chunks.asScala.foreach(result.put)
    chunks.clear()
    resultPromise.success(result)
  }

  def cancel(): Unit =
    // subscription.cancel is idempotent:
    // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.3/README.md#specification
    // so the following can be safely retried
    subscription.updateAndGet(new UnaryOperator[(Boolean, Subscription)] {
      override def apply(current: (Boolean, Subscription)): (Boolean, Subscription) = {
        if (current._2 != null) current._2.cancel()
        (true, null)
      }
    })
}

object SimpleSubscriber {
  def readAll(publisher: Publisher[HttpContent], maxBytes: Option[Long]) = 
    new LimitedLengthSubscriber(maxBytes, new SimpleSubscriber())
}
