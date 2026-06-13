package sttp.tapir.server.netty.internal

import io.netty.handler.codec.http.multipart.{DefaultHttpDataFactory, HttpDataFactory}
import sttp.tapir.TapirFile

object NettyHelper {
  def createHttpDataFactory(minSizeForDisk: Option[Long], tempDirectory: Option[TapirFile]): HttpDataFactory = {
    val factory = minSizeForDisk match {
      case Some(minSize) => new DefaultHttpDataFactory(minSize)
      case None          => new DefaultHttpDataFactory()
    }
    tempDirectory.foreach(dir => factory.setBaseDir(dir.getPath))
    factory
  }

}
