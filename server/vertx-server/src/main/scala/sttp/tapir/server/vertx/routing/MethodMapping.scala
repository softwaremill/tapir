package sttp.tapir.server.vertx.routing

import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import sttp.model.Method

/** Utility object to convert HTTP methods between Vert.x and Tapir
  */
private[vertx] object MethodMapping {

  private val conversions = Map(
    Method.CONNECT -> HttpMethod.CONNECT,
    Method.DELETE -> HttpMethod.DELETE,
    Method.GET -> HttpMethod.GET,
    Method.HEAD -> HttpMethod.HEAD,
    Method.OPTIONS -> HttpMethod.OPTIONS,
    Method.PATCH -> HttpMethod.PATCH,
    Method.POST -> HttpMethod.POST,
    Method.PUT -> HttpMethod.PUT,
    Method.TRACE -> HttpMethod.TRACE
  )

  def sttpToVertx(method: Option[Method]): Option[HttpMethod] =
    method.flatMap(conversions.get)

  def vertxToSttp(request: HttpServerRequest): Method =
    Method.unsafeApply(request.method.name.toUpperCase)

}
