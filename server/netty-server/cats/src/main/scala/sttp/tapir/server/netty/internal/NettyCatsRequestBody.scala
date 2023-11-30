package sttp.tapir.server.netty.internal

import cats.effect.{Async, Sync}
import cats.syntax.all._
import fs2.Chunk
import fs2.interop.reactivestreams.StreamSubscriber
import fs2.io.file.{Files, Path}
import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.fs2.Fs2Streams
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.reactivestreams.NettyRequestBody
import sttp.tapir.TapirFile

private[netty] class NettyCatsRequestBody[F[_]: Async](val createFile: ServerRequest => F[TapirFile])
    extends NettyRequestBody[F, Fs2Streams[F]] {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override implicit val monad: MonadError[F] = new CatsMonadError()

  override def publisherToBytes(publisher: Publisher[HttpContent], maxBytes: Option[Long]): F[Array[Byte]] =
    publisherToStream(publisher, maxBytes).compile.to(Chunk).map(_.toArray[Byte])

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): F[Unit] =
    toStream(serverRequest, maxBytes)
      .through(
        Files[F](Files.forAsync[F]).writeAll(Path.fromNioPath(file.toPath))
      )
      .compile
      .drain

  override def publisherToStream(publisher: Publisher[HttpContent], maxBytes: Option[Long]): streams.BinaryStream = {
    val stream = fs2.Stream
      .eval(StreamSubscriber[F, HttpContent](NettyIdRequestBody.DefaultChunkSize))
      .flatMap(s => s.sub.stream(Sync[F].delay(publisher.subscribe(s))))
      .flatMap(httpContent => fs2.Stream.chunk(Chunk.byteBuffer(httpContent.content.nioBuffer())))
    maxBytes.map(Fs2Streams.limitBytes(stream, _)).getOrElse(stream)
  }

  override def failedStream(e: => Throwable): streams.BinaryStream =
    fs2.Stream.raiseError(e)

  override def emptyStream: streams.BinaryStream =
    fs2.Stream.empty
}
