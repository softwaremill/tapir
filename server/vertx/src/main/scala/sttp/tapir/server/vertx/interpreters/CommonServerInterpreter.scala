package sttp.tapir.server.vertx.interpreters

import io.vertx.ext.web.{Route, Router}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.vertx.handlers.attachDefaultHandlers
import sttp.tapir.server.vertx.routing.PathMapping.{RouteDefinition, createRoute}
import sttp.tapir.server.vertx.streams.ReadStreamCompatible

trait CommonServerInterpreter {
  protected def mountWithDefaultHandlers[F[_], I, E, O, C, S, BS](e: ServerEndpoint[I, E, O, C, F])(
      router: Router,
      routeDef: RouteDefinition
  )(implicit readStreamCompatible: ReadStreamCompatible[S, BS]): Route =
    attachDefaultHandlers(e.endpoint, createRoute(router, routeDef))
}
