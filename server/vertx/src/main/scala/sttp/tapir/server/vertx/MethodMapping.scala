package sttp.tapir.server.vertx

import io.vertx.core.http.HttpMethod
import io.vertx.scala.core.http.HttpServerRequest
import sttp.model.Method

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
    Method.TRACE -> HttpMethod.TRACE,
  )

  def sttpToVertx(method: Option[Method]): Option[HttpMethod] =
    method.flatMap(conversions.get)

  def vertxToSttp(request: HttpServerRequest): Method =
    Method(request.rawMethod().toUpperCase)

}
