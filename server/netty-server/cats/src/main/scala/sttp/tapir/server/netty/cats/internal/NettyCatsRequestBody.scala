package sttp.tapir.server.netty.cats.internal

import cats.effect.Async
import cats.effect.kernel.{Resource, Sync}
import cats.syntax.all._
import fs2.Chunk
import fs2.interop.reactivestreams.StreamSubscriber
import fs2.io.file.{Files, Path}
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.multipart.{DefaultHttpDataFactory, HttpData, HttpPostRequestDecoder}
import org.playframework.netty.http.StreamedHttpRequest
import org.reactivestreams.Publisher
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.capabilities.fs2.Fs2Streams
import sttp.model.Part
import sttp.monad.MonadError
import sttp.tapir.integ.cats.effect.CatsMonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.netty.internal.{NettyStreamingRequestBody, StreamCompatible}
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

import java.io.File


private[cats] class NettyCatsRequestBody[F[_]: Async](
    val createFile: ServerRequest => F[TapirFile],
    val streamCompatible: StreamCompatible[Fs2Streams[F]]
) extends NettyStreamingRequestBody[F, Fs2Streams[F]] {

  override implicit val monad: MonadError[F] = new CatsMonadError()

  def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): F[RawValue[Seq[RawPart]]] = {
    fs2.Stream
      .resource(
        Resource.make(Sync[F].delay(new HttpPostRequestDecoder(NettyCatsRequestBody.multiPartDataFactory, nettyRequest)))(d =>
          Sync[F].blocking(d.destroy()) // after the stream finishes or fails, decoder data has to be cleaned up
        )
      )
      .flatMap { decoder =>
        fs2.Stream
          .eval(StreamSubscriber[F, HttpContent](bufferSize = 1))
          .flatMap(s => s.sub.stream(Sync[F].delay(nettyRequest.subscribe(s))))
          .evalMapAccumulate({
            (decoder, 0L)
          })({ case ((decoder, processedBytesNum), httpContent) =>
            monad
              .blocking {
                // this operation is the one that does potential I/O (writing files)
                decoder.offer(httpContent)
                var processedBytesAndContentBytes = processedBytesNum

                val parts = Stream
                  .continually(if (decoder.hasNext) {
                    val next = decoder.next()
                    processedBytesAndContentBytes = processedBytesAndContentBytes + next.asInstanceOf[HttpData].length()
                    maxBytes.foreach { max =>
                      if (max < processedBytesAndContentBytes) {
                        throw new StreamMaxLengthExceededException(max)
                      }
                    }
                    next
                  } else null)
                  .takeWhile(_ != null)
                  .toVector
                (
                  (decoder, processedBytesAndContentBytes),
                  parts
                )
              }
          })
          .map(_._2)
          .map(_.flatMap(p => m.partType(p.getName()).map((p, _)).toList))
          .evalMap(_.traverse { case (data, partType) => toRawPart(serverRequest, data, partType).map(_.asInstanceOf[Part[Any]]) })
      }
      .compile
      .toVector
      .map(_.flatten)
      .map(RawValue.fromParts(_))
  }

  override def publisherToBytes(publisher: Publisher[HttpContent], contentLength: Option[Long], maxBytes: Option[Long]): F[Array[Byte]] =
    streamCompatible.fromPublisher(publisher, maxBytes).compile.to(Chunk).map(_.toArray[Byte])

  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): F[Unit] =
    (toStream(serverRequest, maxBytes)
      .asInstanceOf[streamCompatible.streams.BinaryStream])
      .through(
        Files[F](Files.forAsync[F]).writeAll(Path.fromNioPath(file.toPath))
      )
      .compile
      .drain

  override def writeBytesToFile(bytes: Array[Byte], file: File): F[Unit] =
    fs2.Stream.emits(bytes).through(Files.forAsync[F].writeAll(Path.fromNioPath(file.toPath))).compile.drain

}

private[cats] object NettyCatsRequestBody {
  val multiPartDataFactory =
    new DefaultHttpDataFactory() // writes to memory, then switches to disk if exceeds MINSIZE (16kB), check other constructors.
}
