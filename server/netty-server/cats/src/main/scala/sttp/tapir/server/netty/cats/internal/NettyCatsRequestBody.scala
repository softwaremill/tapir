package sttp.tapir.server.netty.cats.internal

import cats.effect.Async
import cats.syntax.all._
import fs2.Chunk
import fs2.io.file.{Files, Path}
import io.netty.handler.codec.http.HttpContent
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.netty.internal.{NettyStreamingRequestBody, StreamCompatible}
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

private[cats] class NettyCatsRequestBody[F[_]: Async](
    val createFile: ServerRequest => F[TapirFile],
    val streamCompatible: StreamCompatible[Fs2Streams[F]]
) extends NettyStreamingRequestBody[F, Fs2Streams[F]] {

  override implicit val monad: MonadError[F] = new CatsMonadError()

  override def publisherToBytes(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): F[Array[Byte]] =
    streamCompatible.fromPublisher(publisher, maxBytes).compile.to(Chunk).map(_.toArray[Byte])

  def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): F[RawValue[Seq[RawPart]]] = monad.error(new UnsupportedOperationException("Multipart requests are not supported"))

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): F[Unit] =
    (toStream(serverRequest, maxBytes)
      .asInstanceOf[streamCompatible.streams.BinaryStream])
      .through(
        Files[F](Files.forAsync[F]).writeAll(Path.fromNioPath(file.toPath))
      )
      .compile
      .drain

  override def writeBytesToFile(bytes: Array[Byte], file: TapirFile): F[Unit] =
    monad.error(new UnsupportedOperationException("Multipart requests are not supported"))
}
