package sttp.tapir.server.armeria

import com.linecorp.armeria.common.HttpMethod
import sttp.model.Method

/** Utility object to convert HTTP methods between Armeria and Tapir. */
private[armeria] object MethodMapping {

  private val sttpToArmeria: Map[Method, HttpMethod] = Map(
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

  private val armeriaToSttp: Map[HttpMethod, Method] = Map(
    HttpMethod.CONNECT -> Method.CONNECT,
    HttpMethod.DELETE -> Method.DELETE,
    HttpMethod.GET -> Method.GET,
    HttpMethod.HEAD -> Method.HEAD,
    HttpMethod.OPTIONS -> Method.OPTIONS,
    HttpMethod.PATCH -> Method.PATCH,
    HttpMethod.POST -> Method.POST,
    HttpMethod.PUT -> Method.PUT,
    HttpMethod.TRACE -> Method.TRACE
  )

  def toArmeria(method: Method): HttpMethod = sttpToArmeria(method)

  def fromArmeria(method: HttpMethod): Method = armeriaToSttp(method)
}
