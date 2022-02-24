package sttp.tapir.server.armeria.zio

import _root_.zio._
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.armeria.TapirService
import sttp.tapir.ztapir.ZServerEndpoint

trait ArmeriaZioServerInterpreter[R] {

  def armeriaServerOptions: ArmeriaZioServerOptions[RIO[R, *]]

  def toService(serverEndpoints: ZServerEndpoint[R, ZioStreams])(implicit runtime: Runtime[R]): TapirService[ZioStreams, RIO[R, *]] =
    toService(List(serverEndpoints))

  def toService(serverEndpoints: List[ZServerEndpoint[R, ZioStreams]])(implicit runtime: Runtime[R]): TapirService[ZioStreams, RIO[R, *]] =
    TapirZioService(serverEndpoints, armeriaServerOptions)
}

object ArmeriaZioServerInterpreter {
  def apply[R](
      serverOptions: ArmeriaZioServerOptions[RIO[R, *]] = ArmeriaZioServerOptions.default[R]
  ): ArmeriaZioServerInterpreter[R] = {
    new ArmeriaZioServerInterpreter[R] {
      override def armeriaServerOptions: ArmeriaZioServerOptions[RIO[R, *]] = serverOptions
    }
  }
}
