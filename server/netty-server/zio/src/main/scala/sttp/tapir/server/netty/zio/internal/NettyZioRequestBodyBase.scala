package sttp.tapir.server.netty.zio.internal

import io.netty.handler.codec.http.HttpContent
import org.reactivestreams.Publisher
import sttp.capabilities.zio.ZioStreams
import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.netty.internal.{NettyStreamingRequestBody, StreamCompatible}
import sttp.tapir.ztapir.RIOMonadError
import sttp.tapir.TapirFile
import zio.stream._
import zio.RIO

import java.nio.file.Files
import scala.util.Try

abstract class NettyZioRequestBodyBase[Env](
    val createFile: ServerRequest => RIO[Env, TapirFile],
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long],
    val streamCompatible: StreamCompatible[ZioStreams]
) extends NettyStreamingRequestBody[RIO[Env, *], ZioStreams](multipartTempDirectory, multipartMinSizeForDisk) {

  override val streams: ZioStreams = ZioStreams
  override implicit val monad: MonadError[RIO[Env, *]] = new RIOMonadError[Env]

  override def publisherToBytes(
      publisher: Publisher[HttpContent],
      contentLength: Option[Long],
      maxBytes: Option[Long]
  ): RIO[Env, Array[Byte]] =
    streamCompatible.fromPublisher(publisher, maxBytes).run(ZSink.collectAll[Byte]).map(_.toArray)


  override def writeToFile(serverRequest: ServerRequest, file: TapirFile, maxBytes: Option[Long]): RIO[Env, Unit] =
    toStream(serverRequest, maxBytes).run(ZSink.fromFile(file)).map(_ => ())

  override def writeBytesToFile(bytes: Array[Byte], file: TapirFile): RIO[Env, Unit] =
    monad.fromTry(Try(Files.write(file.toPath, bytes)))

}
