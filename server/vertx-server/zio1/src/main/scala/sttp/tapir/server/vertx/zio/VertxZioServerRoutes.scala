package sttp.tapir.server.vertx.zio

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ServerRoutes
import sttp.tapir.ztapir.ZServerEndpoint
import zio.Runtime
import zio.blocking.Blocking

object VertxZioServerRoutes {

  implicit class VertxZioServerEndpointOps[R <: Blocking](se: ZServerEndpoint[R, ZioStreams]) {
    def route(interpreter: VertxZioServerInterpreter[R])(implicit runtime: Runtime[R]): VertxZioServerRoutes[R] =
      ServerRoutes.one(se)(interpreter.route)
  }
}
