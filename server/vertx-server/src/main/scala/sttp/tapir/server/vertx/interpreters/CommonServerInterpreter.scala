package sttp.tapir.server.vertx.interpreters

import io.vertx.ext.web.{Route, Router}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.vertx.VertxServerOptions
import sttp.tapir.server.vertx.handlers.attachDefaultHandlers
import sttp.tapir.server.vertx.routing.PathMapping.{RouteDefinition, createOptionsRoute, createRoute}

trait CommonServerInterpreter {
  protected def optionsRoute[C, F[_]](e: ServerEndpoint[C, F])(router: Router, routeDef: RouteDefinition): Option[Route] =
    createOptionsRoute(router, routeDef)

  protected def mountWithDefaultHandlers[C, F[_]](e: ServerEndpoint[C, F])(
      router: Router,
      routeDef: RouteDefinition,
      serverOptions: VertxServerOptions[F]
  ): Route =
    attachDefaultHandlers(e.endpoint, createRoute(router, routeDef), serverOptions.uploadDirectory.getPath)
}
