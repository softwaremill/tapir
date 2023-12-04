package sttp.tapir.server.netty.internal.reactivestreams

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.{Publisher, Subscription}

import java.nio.channels.AsynchronousFileChannel
import java.nio.file.{Path, StandardOpenOption}
import scala.concurrent.{Future, Promise}
import java.util.concurrent.LinkedBlockingQueue

/** A Reactive Streams subscriber which receives chunks of bytes and writes them to a file.
  */
class FileWriterSubscriber(path: Path) extends PromisingSubscriber[Unit, HttpContent] {
  private var subscription: Subscription = _

  /** JDK interface to write asynchronously to a file */
  private var fileChannel: AsynchronousFileChannel = _

  /** Current position in the file */
  @volatile private var position: Long = 0

  /** Used to signal completion, so that external code can represent writing to a file as Future[Unit] */
  private val resultPromise = Promise[Unit]()

  /** An alternative way to signal completion, so that non-effectful servers can await on the response (like netty-loom) */
  private val resultBlockingQueue = new LinkedBlockingQueue[Either[Throwable, Unit]]()

  override def future: Future[Unit] = resultPromise.future
  private def waitForResultBlocking(): Either[Throwable, Unit] = resultBlockingQueue.take()

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
    resultBlockingQueue.add(Left(t))
    resultPromise.failure(t)
  }

  override def onComplete(): Unit = {
    fileChannel.close()
    val _ = resultBlockingQueue.add(Right(()))
    resultPromise.success(())
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
    subscriber.waitForResultBlocking().left.foreach(e => throw e)
  }
}
