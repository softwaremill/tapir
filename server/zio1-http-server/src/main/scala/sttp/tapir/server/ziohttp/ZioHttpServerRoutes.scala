package sttp.tapir.server.ziohttp

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerRoutes
import sttp.tapir.ztapir.ZServerEndpoint

object ZioHttpServerRoutes {

  implicit class ZioHttpServerEndpointOps[R](se: ZServerEndpoint[R, ZioStreams]) {
    def toHttp(interpreter: ZioHttpInterpreter[R]): ZioHttpServerRoutes[R] =
      ServerRoutes.one(se)(interpreter.toHttp)
  }

  implicit class ZioHttpServerEndpointListOps[R](se: List[ZServerEndpoint[R, ZioStreams]]) {
    def toHttp(interpreter: ZioHttpInterpreter[R]): ZioHttpServerRoutes[R] =
      ServerRoutes.fromList(se)(interpreter.toHttp)
  }

}
