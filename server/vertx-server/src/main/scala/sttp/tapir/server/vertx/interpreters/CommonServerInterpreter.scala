package sttp.tapir.server.vertx.interpreters

import io.vertx.core.http.HttpMethod._
import io.vertx.ext.web.{Route, Router}
import sttp.model.Method.HEAD
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.vertx.VertxServerOptions
import sttp.tapir.server.vertx.handlers.attachDefaultHandlers
import sttp.tapir.server.vertx.routing.PathMapping.{RouteDefinition, createRoute}

trait CommonServerInterpreter {

  /** Checks if a CORS interceptor is defined in the server options and creates an OPTIONS route if it is.
    *
    * Vert.x will signal a 405 error if a route matches the path, but doesn’t match the HTTP Method. So if CORS is defined, we additionally
    * register OPTIONS route which accepts the preflight requests.
    *
    * @return
    *   An optional Route. If a CORS interceptor is defined, an OPTIONS route is created and returned. Otherwise, None is returned.
    */
  protected def optionsRouteIfCORSDefined[C, F[_]](
      e: ServerEndpoint[C, F]
  )(router: Router, routeDef: RouteDefinition, serverOptions: VertxServerOptions[F]): Option[Route] = {
    def isCORSInterceptorDefined[F[_]](interceptors: List[Interceptor[F]]): Boolean = {
      interceptors.collectFirst { case ci: CORSInterceptor[F] => ci }.nonEmpty
    }

    def createOptionsRoute(router: Router, route: RouteDefinition): Option[Route] =
      route match {
        case (Some(method), path) if Set(GET, HEAD, POST, PUT, DELETE).contains(method) =>
          Some(router.options(path))
        case (None, path) => Some(router.options(path))
        case _            => None
      }

    if (isCORSInterceptorDefined(serverOptions.interceptors)) {
      createOptionsRoute(router, routeDef)
    } else
      None
  }

  protected def mountWithDefaultHandlers[C, F[_]](e: ServerEndpoint[C, F])(
      router: Router,
      routeDef: RouteDefinition,
      serverOptions: VertxServerOptions[F]
  ): Route =
    attachDefaultHandlers(e.endpoint, createRoute(router, routeDef), serverOptions.uploadDirectory.getPath)
}
