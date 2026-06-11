package sttp.tapir.server.netty.internal

import org.playframework.netty.http.StreamedHttpRequest
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue

import sttp.tapir.{RawBodyType, RawPart, TapirFile}

import scala.concurrent.{ExecutionContext, Future}

private[netty] class NettyFutureRequestBody(
    val createFile: ServerRequest => Future[TapirFile],
    val multipartTempDirectory: Option[TapirFile],
    val multipartMinSizeForDisk: Option[Long]
)(implicit ec: ExecutionContext)
    extends NettyFutureRequestBodyBase {

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): streams.BinaryStream =
    throw new UnsupportedOperationException()

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): Future[RawValue[Seq[RawPart]]] = Future.failed(new UnsupportedOperationException("Multipart requests are not supported"))


}
