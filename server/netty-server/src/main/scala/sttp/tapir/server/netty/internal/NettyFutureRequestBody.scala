package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.HttpContent
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities
import sttp.monad.{FutureMonad, MonadError}
import sttp.tapir.capabilities.NoStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.reactivestreams._
import sttp.tapir.{RawPart, TapirFile}

import scala.concurrent.{ExecutionContext, Future}

private[netty] class NettyFutureRequestBody(
    val createFile: ServerRequest => Future[TapirFile],
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long]
)(implicit ec: ExecutionContext)
    extends NettyRequestBodyWithMultipartF[Future, NoStreams](multipartTempDirectory, multipartMinSizeForDisk) {

  override val streams: capabilities.Streams[NoStreams] = NoStreams
  override implicit val monad: MonadError[Future] = new FutureMonad()

  protected def listMonadToMonadOfList(l: List[Future[RawPart]]): Future[List[RawPart]] = Future.sequence(l)

  override protected def fromFuture[T](f: Future[T]): Future[T] = f

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

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()
}
