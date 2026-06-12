package sttp.tapir.server.netty.internal

import org.playframework.netty.http.StreamedHttpRequest
import ox.Chunk
import ox.flow.Flow
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.{RawBodyType, RawPart, TapirFile}

import scala.concurrent.{ExecutionContext, Future}

private[netty] class NettyFutureRequestBody(
    val createFile: ServerRequest => Future[TapirFile],
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long]
)(implicit ec: ExecutionContext)
    extends NettyFutureRequestBodyBase(multipartTempDirectory, multipartMinSizeForDisk) {

  override def toStream(serverRequest: ServerRequest, maxBytes: Option[Long]): Flow[Chunk[Byte]] =
    NettyOsxHelper.toStream(serverRequest, maxBytes)

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): Future[RawValue[Seq[RawPart]]] =
    NettyOsxHelper.publishToMultipartF(nettyRequest, serverRequest, m, maxBytes)(httpDataFactory, toRawPart, s => Future.sequence(s))

}
