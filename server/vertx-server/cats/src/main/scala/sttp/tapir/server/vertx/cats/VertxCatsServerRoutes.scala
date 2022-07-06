package sttp.tapir.server.vertx.cats

import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.integ.cats.ServerRoutesInstances
import sttp.tapir.server.{ServerEndpoint, ServerRoutes}

object VertxCatsServerRoutes extends ServerRoutesInstances {

  implicit class VertxCatsServerEndpointOps[F[_]](se: ServerEndpoint[Fs2Streams[F], F]) {
    def route(interpreter: VertxCatsServerInterpreter[F]): VertxPlayServerRoutes[F] =
      ServerRoutes.one(se)(interpreter.route)
  }
}
