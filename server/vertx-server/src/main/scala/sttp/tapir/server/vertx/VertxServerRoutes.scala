package sttp.tapir.server.vertx

import io.vertx.ext.web.{Route, Router}
import sttp.tapir.server.{ServerEndpoint, ServerEndpointToRoutes}

object VertxServerRoutes extends ServerRoutesInstances {

  implicit def serverEndpointToRoutes[F[_]]: ServerEndpointToRoutes[
    ServerEndpoint[Fs2Streams[F], F],
    VertxCatsServerInterpreter[F],
    Router => Route
  ] = ServerEndpointToRoutes.of(_.route(_))
}
