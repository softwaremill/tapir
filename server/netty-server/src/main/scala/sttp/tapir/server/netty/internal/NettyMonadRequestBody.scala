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
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.concurrent.{Future, Promise}

/** Generic implementation of NettyRequestBody, for any effect F[_] and any stream type S. */
private[netty] trait NettyMonadRequestBody[F[_], S <: Streams[S]] extends NettyRequestBody[F, S] {

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
          httpContent.release()
          onError(StreamMaxLengthExceededException(max))
        case _ =>
          if (addContentSafe(httpContent)) {
            val _ = actionOrOnError(
              {
                val parts = Iterator
                  .continually(maybeNext())
                  .takeWhile(_.nonEmpty)
                  .flatten
                  .flatMap(toPart)
                  .toList
                acc.getAndAccumulate(parts, (prev, toAdd) => prev ++ toAdd)
                subscription.get().request(1)
              },
              ()
            )
          }
      }
    }

    override def onError(t: Throwable): Unit = {
      subscription.get().cancel()
      val _ = promise.tryFailure(t)
      decoder.destroy()
    }

    override def onComplete(): Unit = {
      val f = seqMonadToMonadOfSeq(acc.get())
      val r = f.map(p => RawValue.fromParts(p).copy(cleanup = Option(() => decoder.destroy())))
      val _ = promise.trySuccess(r)
    }

    private def addContentSafe(httpContent: HttpContent): Boolean =
      actionOrOnError(decoder.offer(httpContent), httpContent.release(): Unit)

    private def actionOrOnError[T](action: => T, `finally`: => Unit): Boolean =
      try {
        val _ = action
        true
      } catch {
        case r: RuntimeException =>
          onError(r)
          false
      } finally {
        `finally`
      }

    private def toPart(httpData: InterfaceHttpData): Option[F[RawPart]] =
      m.partType(httpData.getName).map(partType => toRawPart(serverRequest, httpData, partType).map(identity))

    private def maybeNext(): Option[InterfaceHttpData] = if (decoder.hasNext) Option(decoder.next()) else None
  }

  override def writeBytesToFile(bytes: Array[Byte], file: TapirFile): F[Unit] =
    fromFuture(writeBytesToFileFuture(bytes, file))

  private def writeBytesToFileFuture(bytes: Array[Byte], file: TapirFile): Future[Unit] = {
    val promise = Promise[Unit]()
    val channel = AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    channel.write(
      ByteBuffer.wrap(bytes),
      0,
      channel,
      new CompletionHandler[Integer, AutoCloseable]() {
        override def completed(result: Integer, closeable: AutoCloseable): Unit = {
          promise.success(())
          closeable.close()
        }

        override def failed(exc: Throwable, closeable: AutoCloseable): Unit = {
          promise.failure(exc)
          closeable.close()
        }
      }
    )
    promise.future
  }

}
