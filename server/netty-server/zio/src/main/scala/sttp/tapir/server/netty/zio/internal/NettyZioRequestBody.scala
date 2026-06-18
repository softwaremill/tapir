package sttp.tapir.server.netty.zio.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.{NettyStreamingRequestBody, StreamCompatible}
import sttp.tapir.ztapir.RIOMonadError
import sttp.tapir.{RawPart, TapirFile}
import zio.stream._
import zio.{RIO, ZIO}

import scala.concurrent.Future

private[zio] class NettyZioRequestBody[Env](
    val createFile: ServerRequest => RIO[Env, TapirFile],
    val streamCompatible: StreamCompatible[ZioStreams],
    val multipartTempDirectory: Option[TapirFile] = None,
    val multipartMinSizeForDisk: Option[Long] = None
) extends NettyStreamingRequestBody[RIO[Env, *], ZioStreams] {

  override val streams: ZioStreams = ZioStreams
  override implicit val monad: MonadError[RIO[Env, *]] = new RIOMonadError[Env]

  override protected def listMonadToMonadOfList(l: List[RIO[Env, RawPart]]): RIO[Env, List[RawPart]] = ZIO.collectAll(l)

  override protected def fromFuture[T](f: Future[T]): RIO[Env, T] = ZIO.fromFuture(_ => f)

  override def publisherToBytes(
      publisher: Publisher[HttpContent],
      contentLength: Option[Long],
      maxBytes: Option[Long]
  ): RIO[Env, Array[Byte]] =
    streamCompatible.fromPublisher(publisher, maxBytes).run(ZSink.collectAll[Byte]).map(_.toArray)

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): RIO[Env, Unit] =
    toStream(serverRequest, maxBytes).run(ZSink.fromFile(file)).map(_ => ())

}
