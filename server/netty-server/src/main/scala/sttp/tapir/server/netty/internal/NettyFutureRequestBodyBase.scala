package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.NettyStreams
import sttp.tapir.server.netty.internal.reactivestreams._

import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}
import scala.compat.java8.FutureConverters

private[netty] abstract class NettyFutureRequestBodyBase(implicit ec: ExecutionContext) extends NettyRequestBody[Future, NettyStreams] {
  override val streams: NettyStreams = NettyStreams
  override implicit val monad: MonadError[Future] = new FutureMonad()

  override def publisherToBytes(
      publisher: Publisher[HttpContent],
      contentLength: Option[Long],
      maxBytes: Option[Long]
  ): Future[Array[Byte]] =
    SimpleSubscriber.processAll(publisher, contentLength, maxBytes)

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): Future[Unit] =
    serverRequest.underlying match {
      case r: StreamedHttpRequest => FileWriterSubscriber.processAll(r, file.toPath, maxBytes)
      case _                      => monad.unit(()) // Empty request
    }

  override def writeBytesToFile(bytes: Array[Byte], file: TapirFile): Future[Unit] = {
    val javaFuture =
      AsynchronousFileChannel.open(file.toPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE).write(ByteBuffer.wrap(bytes), 0)
    for { _ <- FutureConverters.toScala(CompletableFuture.supplyAsync(() => javaFuture.get)) } yield ()
  }

}
