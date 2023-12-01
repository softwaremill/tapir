package sttp.tapir.server.netty.cats.internal

import cats.effect.Async
import cats.syntax.all._
import fs2.Chunk
import fs2.io.file.{Files, Path}
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.TapirFile
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.{NettyStreamingRequestBody, StreamCompatible}

private[cats] class NettyCatsRequestBody[F[_]: Async](
    val createFile: ServerRequest => F[TapirFile],
    val streamCompatible: StreamCompatible[Fs2Streams[F]]
) extends NettyStreamingRequestBody[F, Fs2Streams[F]] {

  override implicit val monad: MonadError[F] = new CatsMonadError()

  override def publisherToBytes(publisher: Publisher[HttpContent], maxBytes: Option[Long]): F[Array[Byte]] =
    streamCompatible.fromPublisher(publisher, maxBytes).compile.to(Chunk).map(_.toArray[Byte])

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): F[Unit] =
    (toStream(serverRequest, maxBytes).asInstanceOf[streamCompatible.streams.BinaryStream])
      .through(
        Files[F](Files.forAsync[F]).writeAll(Path.fromNioPath(file.toPath))
      )
      .compile
      .drain
}
