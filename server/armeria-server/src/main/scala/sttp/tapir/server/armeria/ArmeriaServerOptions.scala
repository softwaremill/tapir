package sttp.tapir.server.armeria

import sttp.tapir.TapirFile
import sttp.tapir.server.interceptor.Interceptor

trait ArmeriaServerOptions[F[_]] {
  def createFile: () => F[TapirFile]
  def deleteFile: TapirFile => F[Unit]
  def interceptors: List[Interceptor[F]]
}
