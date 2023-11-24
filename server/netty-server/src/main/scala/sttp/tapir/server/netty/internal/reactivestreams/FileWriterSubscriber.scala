package sttp.tapir.server.netty.internal.reactivestreams

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{Path, StandardOpenOption}
import org.reactivestreams.{Subscriber, Subscription}
import io.netty.handler.codec.http.HttpContent
import java.io.IOException
import scala.concurrent.Promise
import org.reactivestreams.Publisher
import scala.concurrent.Future

class FileWriterSubscriber(path: Path) extends PromisingSubscriber[Unit, HttpContent] {
  private var subscription: Subscription = _
  private var fileChannel: AsynchronousFileChannel = _
  private var position: Long = 0
  private val resultPromise = Promise[Unit]()

  override def future: Future[Unit] = resultPromise.future

  override def onSubscribe(s: Subscription): Unit = {
    this.subscription = s
    fileChannel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    s.request(1)
  }

  override def onNext(httpContent: HttpContent): Unit = {
    val byteBuffer = httpContent.content().nioBuffer()
    fileChannel.write(
      byteBuffer,
      position,
      (),
      new java.nio.channels.CompletionHandler[Integer, Unit] {
        override def completed(result: Integer, attachment: Unit): Unit = {
          position += result
          subscription.request(1)
        }

        override def failed(exc: Throwable, attachment: Unit): Unit = {
          onError(exc)
        }
      }
    )
  }

  override def onError(t: Throwable): Unit = {
    fileChannel.close()
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    fileChannel.close()
    resultPromise.success(())
  }
}

object FileWriterSubscriber {
  def writeAll(publisher: Publisher[HttpContent], path: Path, maxBytes: Long): Future[Unit] = {
    val subscriber = new LimitedLengthSubscriber(maxBytes, new FileWriterSubscriber(path))
    publisher.subscribe(subscriber)
    subscriber.future
  }
}
