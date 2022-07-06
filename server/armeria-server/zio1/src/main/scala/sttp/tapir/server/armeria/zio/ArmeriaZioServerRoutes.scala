package sttp.tapir.server.armeria.zio

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerRoutes
import sttp.tapir.ztapir.ZServerEndpoint
import zio.Runtime

object ArmeriaZioServerRoutes {

  implicit class ArmeriaZioServerEndpointOps[R: Runtime](se: ZServerEndpoint[R, ZioStreams]) {
    def toService(interpreter: ArmeriaZioServerInterpreter[R]): ArmeriaZioServerRoutes[R] =
      ServerRoutes.one(se)(interpreter.toService)
  }

  implicit class ArmeriaZioServerEndpointListOps[R: Runtime](se: List[ZServerEndpoint[R, ZioStreams]]) {
    def toService(interpreter: ArmeriaZioServerInterpreter[R]): ArmeriaZioServerRoutes[R] =
      ServerRoutes.fromList(se)(interpreter.toService)
  }
}
