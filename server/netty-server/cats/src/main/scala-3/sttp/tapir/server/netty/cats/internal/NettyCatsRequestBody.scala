package sttp.tapir.server.netty.cats.internal

import cats.effect.Async
import cats.implicits.*
import io.netty.handler.codec.http.multipart.HttpDataFactory
import org.playframework.netty.http.StreamedHttpRequest
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.netty.internal.{NettyHelper, NettyOsxHelper, StreamCompatible}
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

private[cats] class NettyCatsRequestBody[F[_]: Async](
    createFile: ServerRequest => F[TapirFile],
    val multipartTempDirectory: Option[TapirFile],
    val multipartMinSizeForDisk: Option[Long],
    streamCompatible: StreamCompatible[Fs2Streams[F]]
) extends NettyCatsRequestBodyBase[F](createFile, streamCompatible) {

  private val httpDataFactory: HttpDataFactory = NettyHelper.createHttpDataFactory(multipartMinSizeForDisk, multipartTempDirectory)

  def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): F[RawValue[Seq[RawPart]]] =
    NettyOsxHelper.publishToMultipartF(nettyRequest, serverRequest, m, maxBytes)(httpDataFactory, toRawPart, s => s.sequence)

}
