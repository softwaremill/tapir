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
import sttp.capabilities.WebSockets
import org.playframework.netty.http.StreamedHttpRequest
import io.netty.handler.codec.http.multipart.InterfaceHttpData
import fs2.interop.reactivestreams.StreamSubscriber
import cats.effect.kernel.Sync
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder
import sttp.tapir.RawBodyType
import sttp.tapir.RawPart
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory
import sttp.model.Part
import sttp.tapir.server.interceptor.cors.CORSConfig.default

private[cats] class NettyCatsRequestBody[F[_]: Async](
    val createFile: ServerRequest => F[TapirFile],
    val streamCompatible: StreamCompatible[Fs2Streams[F]]
) extends NettyStreamingRequestBody[F, Fs2Streams[F]] {

  override implicit val monad: MonadError[F] = new CatsMonadError()

  def publisherToMultipart(nettyRequest: StreamedHttpRequest): F[Unit] = monad.unit(())

  def publisherToMulti2(nettyRequest: StreamedHttpRequest, m: RawBodyType.MultipartBody): F[Vector[String]] = {

    fs2.Stream
      .eval(StreamSubscriber[F, HttpContent](bufferSize = 1))
      .flatMap(s => s.sub.stream(Sync[F].delay(nettyRequest.subscribe(s))))
      .evalMapAccumulate({
        // initialize the stream's "state" - a mutable, stateful HttpPostRequestDecoder
        new HttpPostRequestDecoder(NettyCatsRequestBody.multiPartDataFactory, nettyRequest)
        //
      })({ case (decoder, httpContent) =>
        if (httpContent.isInstanceOf[LastHttpContent]) {
          monad.blocking {
            decoder.destroy()
            (decoder, Vector.empty)
          }
        } else
          monad
            .blocking {
              // this operation is the one that does potential I/O (writing files)
              decoder.offer(httpContent)
              (
                decoder,
                Stream
                  .continually(decoder.next())
                  .takeWhile(_ => decoder.hasNext)
                  .toVector
              )
            }
            .onError { case _ =>
              monad.blocking(decoder.destroy())
            }
      })
      .map(_._2)
      .map(_.flatMap(p => m.partType(p.getName()).map((p, _)).toList))
      .map(_.map { case (data, partType) => toRawPart(data, partType) })
      .compile
      .toVector
      .map(_.flatten)
  }

  private def toRawPart[R](data: InterfaceHttpData, partType: RawBodyType[R]): String = {
    val partName = data.getName()
    println(partType)
    partName
    // partType match {
    //   case RawBodyType.StringBody(defaultCharset) =>
    //     data.getHttpDataType()
    //     Part(partName, new String(data.getByteBuf().array()), headers = data.getHttpHeaders().entries().map(e => e.getKey() -> e.getValue()).toMap)
    // }
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
}

private[cats] object NettyCatsRequestBody {
  val multiPartDataFactory =
    new DefaultHttpDataFactory(true) // true means always write files to disk, check constructor with minSize for mixed
}
