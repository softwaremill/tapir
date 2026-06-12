package sttp.tapir.server.netty.cats.internal

import cats.effect.Async
import org.playframework.netty.http.StreamedHttpRequest
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.netty.internal.StreamCompatible
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

private[cats] class NettyCatsRequestBody[F[_]: Async](
    createFile: ServerRequest => F[TapirFile],
    val multipartTempDirectory: Option[TapirFile],
    val multipartMinSizeForDisk: Option[Long],
    streamCompatible: StreamCompatible[Fs2Streams[F]]
) extends NettyCatsRequestBodyBase[F](createFile, streamCompatible) {

  def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): F[RawValue[Seq[RawPart]]] = monad.error(new UnsupportedOperationException("Multipart requests are not supported"))

}
