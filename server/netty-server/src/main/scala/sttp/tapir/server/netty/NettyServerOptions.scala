package sttp.tapir.server.netty

import sttp.tapir.TapirFile
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.Interceptor

trait NettyServerOptions[F[_]] {
  def createFile: ServerRequest => F[TapirFile]
  def deleteFile: TapirFile => F[Unit]
  def interceptors: List[Interceptor[F]]
  def multipartTempDirectory: Option[TapirFile]
  def multipartMinSizeForDisk: Option[Long]
}
