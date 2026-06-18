package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.multipart.{DefaultHttpDataFactory, HttpDataFactory}
import sttp.capabilities.Streams
import sttp.tapir.TapirFile

private[netty] trait NettyRequestBodyWithMultipart[F[_], S <: Streams[S]] extends NettyRequestBody[F, S] {
  val multipartTempDirectory: Option[TapirFile]
  val multipartMinSizeForDisk: Option[Long]

  protected val httpDataFactory: HttpDataFactory = {
    val factory = multipartMinSizeForDisk match {
      case Some(minSize) => new DefaultHttpDataFactory(minSize)
      case None          => new DefaultHttpDataFactory()
    }
    multipartTempDirectory.foreach(dir => factory.setBaseDir(dir.getPath))
    factory
  }

}
