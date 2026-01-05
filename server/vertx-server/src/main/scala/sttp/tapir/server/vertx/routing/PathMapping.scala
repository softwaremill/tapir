package sttp.tapir.server.vertx.routing

import io.vertx.core.http.HttpMethod
import io.vertx.ext.web.{Route, Router}
import sttp.tapir.EndpointInput.PathCapture
import sttp.tapir.internal._
import sttp.tapir.{AnyEndpoint, EndpointInput}

object PathMapping {

  // The necessary stuff to create a Route from a Router
  type RouteDefinition = (Option[HttpMethod], String)

  /** Given a Router, creates a Vert.x Route matching the route definition
    * @param router
    *   a Vert.x Router
    * @param route
    *   the definition of the route (method, and path)
    * @return
    *   a route, attached to the router
    */
  private[vertx] def createRoute(router: Router, route: RouteDefinition): Route =
    route match {
      case (Some(method), path) => router.route(method, path)
      case (None, path)         => router.route(path)
    }

  /** Extracts the route definition from the endpoint inputs
    * @param endpoint
    *   a Tapir endpoint
    * @return
    *   the route definition matching the endpoint input definition
    */
  private[vertx] def extractRouteDefinition(endpoint: AnyEndpoint): RouteDefinition =
    (MethodMapping.sttpToVertx(endpoint.method), extractVertxPath(endpoint))

  private def extractVertxPath(endpoint: AnyEndpoint): String = {
    var idxUsed = 0
    val path = endpoint
      .asVectorOfBasicInputs()
      .collect {
        case segment: EndpointInput.FixedPath[_] => segment.show
        case PathCapture(Some(name), _, _)       => s"/:$name"
        case PathCapture(_, _, _)                =>
          idxUsed += 1
          s"/:param$idxUsed"
        case EndpointInput.PathsCapture(_, _) => "/*"
      }
      .mkString
    if (path.isEmpty) "/*" else path
  }
}
