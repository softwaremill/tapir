package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.multipart.{DefaultHttpDataFactory, HttpDataFactory}
import sttp.capabilities.Streams
import sttp.tapir.TapirFile

private[netty] abstract class NettyRequestBodyWithMultipart[F[_], S <: Streams[S]](
    multipartTempDirectory: Option[TapirFile],
    multipartMinSizeForDisk: Option[Long]
) extends NettyRequestBody[F, S] {
  protected val httpDataFactory: HttpDataFactory = {
    val factory = multipartMinSizeForDisk match {
      case Some(minSize) => new DefaultHttpDataFactory(minSize)
      case None          => new DefaultHttpDataFactory()
    }
    multipartTempDirectory.foreach(dir => factory.setBaseDir(dir.getPath))
    factory
  }

}
