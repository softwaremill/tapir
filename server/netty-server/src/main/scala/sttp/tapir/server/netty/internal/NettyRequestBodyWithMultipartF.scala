package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.multipart.{HttpPostMultipartRequestDecoder, InterfaceHttpData}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.{Subscriber, Subscription}
import sttp.capabilities.{StreamMaxLengthExceededException, Streams}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.StandardOpenOption
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import java.util.concurrent.locks.ReentrantLock
import scala.concurrent.Future
import scala.compat.java8.FutureConverters._

private[netty] abstract class NettyRequestBodyWithMultipartF[F[_], S <: Streams[S]](
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long],
    seqMonadToMonadOfSeq: Seq[F[RawPart]] => F[Seq[RawPart]]
) extends NettyRequestBodyWithMultipart[F, S](multipartTempDirectory, multipartMinSizeForDisk) {

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): F[RawValue[Seq[RawPart]]] = {

    val lock = new ReentrantLock()
    val condition = lock.newCondition

    def locked[T](code: => T): T =
      try {
        lock.lock()
        code
      } finally {
        lock.unlock()
      }

    def release(): Unit =
      locked(condition.signal())

    val result = new AtomicReference[F[RawValue[Seq[RawPart]]]]()
    val subscriber = new MonadSubscriber(nettyRequest, serverRequest, m, maxBytes, release, seqMonadToMonadOfSeq)(result)
    nettyRequest.subscribe(subscriber)

    locked {
      condition.await()
      result.get()
    }

  }

  private class MonadSubscriber(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long],
      release: () => Unit,
      seqMonadToMonadOfSeq: Seq[F[RawPart]] => F[Seq[RawPart]]
  )(
      result: AtomicReference[F[RawValue[Seq[RawPart]]]]
  ) extends Subscriber[HttpContent] {
    private val currentBytesRead: AtomicLong = new AtomicLong()
    private val decoder = new HttpPostMultipartRequestDecoder(httpDataFactory, nettyRequest)
    private val acc = new AtomicReference[Seq[F[RawPart]]](Seq.empty)
    private val subscription = new AtomicReference[Subscription]()

    override def onSubscribe(s: Subscription): Unit = {
      subscription.set(s)
      subscription.get().request(1)
    }

    override def onNext(httpContent: HttpContent): Unit = {
      val currentRead = currentBytesRead.accumulateAndGet(httpContent.content().readableBytes().toLong, (prev, toAdd) => prev + toAdd)
      maxBytes match {
        case Some(max) if currentRead > max =>
          subscription.get().cancel()
          result.set(monad.error(StreamMaxLengthExceededException(maxBytes.getOrElse(Long.MaxValue))))
          release()
        case _ =>
          decoder.offer(httpContent)
          val ss: Seq[F[RawPart]] = Iterator
            .continually(maybeNext())
            .takeWhile(_.nonEmpty)
            .flatten
            .flatMap(httpData => toPart(httpData))
            .toSeq
          acc.getAndAccumulate(ss, (prev, toAdd) => prev ++ toAdd)
          subscription.get().request(1)
      }
    }

    private def toPart(httpData: InterfaceHttpData): Option[F[RawPart]] =
      m.partType(httpData.getName).map(partType => monad.map(toRawPart(serverRequest, httpData, partType))(identity))

    override def onError(t: Throwable): Unit = {
      result.set(monad.error(t))
      release()
    }

    override def onComplete(): Unit = {
      val f = seqMonadToMonadOfSeq(acc.get().toList)
      val r = monad.map(f)(RawValue.fromParts)
      result.set(r)
      release()
    }

    private def maybeNext(): Option[InterfaceHttpData] = Option.when(decoder.hasNext)(decoder.next())
  }

  protected def writeBytesToFileFuture(bytes: Array[Byte], file: TapirFile): Future[Unit] =
    writeBytesToFileCompletableFuture(bytes, file).toScala

  protected def writeBytesToFileCompletableFuture(bytes: Array[Byte], file: TapirFile): CompletableFuture[Unit] = {
    val javaFuture = new CompletableFuture[Unit]
    val channel = AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    channel.write(
      ByteBuffer.wrap(bytes),
      0,
      channel,
      new CompletionHandler[Integer, AutoCloseable]() {
        override def completed(result: Integer, closeable: AutoCloseable): Unit = {
          javaFuture.complete(())
          closeable.close()
        }

        override def failed(exc: Throwable, attachment: AutoCloseable): Unit = {
          javaFuture.completeExceptionally(exc)
          attachment.close()
        }
      }
    )
    javaFuture
  }
}
