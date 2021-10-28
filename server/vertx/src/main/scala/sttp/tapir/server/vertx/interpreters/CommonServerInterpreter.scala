package sttp.tapir.server.vertx.interpreters

import io.vertx.ext.web.{Route, Router}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.vertx.handlers.attachDefaultHandlers
import sttp.tapir.server.vertx.routing.PathMapping.{RouteDefinition, createRoute}

trait CommonServerInterpreter {
  protected def mountWithDefaultHandlers[F[_], A, U, I, E, O, C](e: ServerEndpoint[A, U, I, E, O, C, F])(
      router: Router,
      routeDef: RouteDefinition
  ): Route =
    attachDefaultHandlers(e.endpoint, createRoute(router, routeDef))
}
