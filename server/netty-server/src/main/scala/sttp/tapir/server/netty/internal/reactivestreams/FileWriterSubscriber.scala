package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}

import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{Path, StandardOpenOption}
import java.util.concurrent.Semaphore
import scala.concurrent.{Future, Promise}

class FileWriterSubscriber(path: Path) extends PromisingSubscriber[Unit, HttpContent] {
  private var subscription: Subscription = _
  private var fileChannel: AsynchronousFileChannel = _
  private var position: Long = 0
  private val resultPromise = Promise[Unit]()
  private val resultBlockingSemaphore: Semaphore = new Semaphore(0)

  override def future: Future[Unit] = resultPromise.future
  def waitForResultBlocking(): Unit = resultBlockingSemaphore.acquire()

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
          subscription.cancel()
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
    resultBlockingSemaphore.release()
  }
}

object FileWriterSubscriber {
  def processAll(publisher: Publisher[HttpContent], path: Path, maxBytes: Option[Long]): Future[Unit] = {
    val subscriber = new FileWriterSubscriber(path)
    publisher.subscribe(maxBytes.map(new LimitedLengthSubscriber(_, subscriber)).getOrElse(subscriber))
    subscriber.future
  }

  def processAllBlocking(publisher: Publisher[HttpContent], path: Path, maxBytes: Option[Long]): Unit = {
    val subscriber = new FileWriterSubscriber(path)
    publisher.subscribe(maxBytes.map(new LimitedLengthSubscriber(_, subscriber)).getOrElse(subscriber))
    subscriber.waitForResultBlocking()
  }
}
