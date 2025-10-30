package sttp.tapir.server.netty.zio.internal

import io.netty.handler.codec.http.HttpContent
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.netty.internal.{NettyStreamingRequestBody, StreamCompatible}
import sttp.tapir.ztapir.RIOMonadError
import sttp.tapir.{RawBodyType, RawPart, TapirFile}
import zio.stream._
import zio.{RIO, ZIO}

private[zio] class NettyZioRequestBody[Env](
    val createFile: ServerRequest => RIO[Env, TapirFile],
    val streamCompatible: StreamCompatible[ZioStreams]
) extends NettyStreamingRequestBody[RIO[Env, *], ZioStreams] {

  override val streams: ZioStreams = ZioStreams
  override implicit val monad: MonadError[RIO[Env, *]] = new RIOMonadError[Env]

  override def publisherToBytes(
      publisher: Publisher[HttpContent],
      contentLength: Option[Long],
      maxBytes: Option[Long]
  ): RIO[Env, Array[Byte]] =
    streamCompatible.fromPublisher(publisher, maxBytes).run(ZSink.collectAll[Byte]).map(_.toArray)

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): RIO[Env, RawValue[Seq[RawPart]]] = ZIO.die(new UnsupportedOperationException("Multipart requests are not supported"))

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): RIO[Env, Unit] =
    toStream(serverRequest, maxBytes).run(ZSink.fromFile(file)).map(_ => ())

  override def writeBytesToFile(bytes: Array[Byte], file: TapirFile): RIO[Env, Unit] =
    ZIO.die(new UnsupportedOperationException("Multipart requests are not supported"))
}
