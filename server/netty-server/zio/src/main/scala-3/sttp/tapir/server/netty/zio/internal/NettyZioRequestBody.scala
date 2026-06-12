package sttp.tapir.server.netty.zio.internal

import org.playframework.netty.http.StreamedHttpRequest
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interpreter.RawValue
import sttp.tapir.server.netty.internal.{NettyOsxHelper, StreamCompatible}
import sttp.tapir.{RawBodyType, RawPart, TapirFile}
import zio.{RIO, ZIO}

private[netty] class NettyZioRequestBody[Env](
    createFile: ServerRequest => RIO[Env, TapirFile],
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long],
    streamCompatible: StreamCompatible[ZioStreams]
) extends NettyZioRequestBodyBase[Env](createFile, multipartTempDirectory, multipartMinSizeForDisk, streamCompatible) {

  override def publisherToMultipart(
      nettyRequest: StreamedHttpRequest,
      serverRequest: ServerRequest,
      m: RawBodyType.MultipartBody,
      maxBytes: Option[Long]
  ): RIO[Env, RawValue[Seq[RawPart]]] =
    NettyOsxHelper.publishToMultipartF(nettyRequest, serverRequest, m, maxBytes)(httpDataFactory, toRawPart, s => ZIO.collectAll(s))

}
