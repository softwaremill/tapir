package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.multipart.{HttpPostMultipartRequestDecoder, InterfaceHttpData}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.{Subscriber, Subscription}
import sttp.capabilities.{StreamMaxLengthExceededException, Streams}
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

import java.nio.channels.{AsynchronousFileChannel, CompletionHandler}
import java.nio.file.StandardOpenOption
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.concurrent.{Future, Promise}
import scala.compat.java8.FutureConverters._

private[netty] abstract class NettyRequestBodyWithMultipartF[F[_], S <: Streams[S]](
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long]
) extends NettyRequestBodyWithMultipart[F, S](multipartTempDirectory, multipartMinSizeForDisk) {

  protected def listMonadToMonadOfList(l: List[F[RawPart]]): F[List[RawPart]]
  protected def fromFuture[T](f: Future[T]): F[T]

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): F[RawValue[Seq[RawPart]]] = {

    val promise = Promise[F[RawValue[Seq[RawPart]]]]()

    val decoder = new HttpPostMultipartRequestDecoder(httpDataFactory, nettyRequest)
    val subscriber = new MonadSubscriber(decoder, serverRequest, m, maxBytes, listMonadToMonadOfList)(promise)
    nettyRequest.subscribe(subscriber)
    val future = promise.future
    val ff = fromFuture(future)
    monad.flatten(ff)

  }

  private class MonadSubscriber(
      decoder: HttpPostMultipartRequestDecoder,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long],
      seqMonadToMonadOfSeq: List[F[RawPart]] => F[List[RawPart]]
  )(
      promise: Promise[F[RawValue[Seq[RawPart]]]]
  ) extends Subscriber[HttpContent] {
    private val currentBytesRead: AtomicLong = new AtomicLong()
    private val acc = new AtomicReference[List[F[RawPart]]](Nil)
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
          promise.failure(StreamMaxLengthExceededException(maxBytes.getOrElse(Long.MaxValue)))
        case _ =>
          try {
            decoder.offer(httpContent)
          } finally {
            val _ = httpContent.release()
          }
          val parts = Iterator
            .continually(maybeNext())
            .takeWhile(_.nonEmpty)
            .flatten
            .flatMap(httpData => toPart(httpData))
            .toList
          acc.getAndAccumulate(parts, (prev, toAdd) => prev ++ toAdd)
          subscription.get().request(1)

      }
    }

    override def onError(t: Throwable): Unit = {
      promise.failure(t)
      decoder.destroy()
    }

    override def onComplete(): Unit = {
      val f = seqMonadToMonadOfSeq(acc.get())
      val r = f.map(p => RawValue.fromParts(p).copy(cleanup = Option(() => decoder.destroy())))
      promise.success(r)
    }

    private def toPart(httpData: InterfaceHttpData): Option[F[RawPart]] =
      m.partType(httpData.getName).map(partType => toRawPart(serverRequest, httpData, partType).map(identity))

    private def maybeNext(): Option[InterfaceHttpData] = if (decoder.hasNext) Option(decoder.next()) else None
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

        override def failed(exc: Throwable, closeable: AutoCloseable): Unit = {
          javaFuture.completeExceptionally(exc)
          closeable.close()
        }
      }
    )
    javaFuture
  }

  override def writeBytesToFile(bytes: Array[Byte], file: TapirFile): F[Unit] =
    fromFuture(writeBytesToFileFuture(bytes, file))
}
