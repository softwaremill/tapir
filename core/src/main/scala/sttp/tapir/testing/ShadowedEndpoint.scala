package sttp.tapir.testing

import sttp.tapir.AnyEndpoint

/** Endpoint `e1` is shadowed by endpoint `e2` when all requests that match `e2` also match `e1`. Here, "request matches endpoint" takes
  * into account only the method & shape of the path. It does *not* take into account possible decoding failures: these might impact
  * request-endpoint matching, and the exact behavior is determined by the
  * [[sttp.tapir.server.interceptor.decodefailure.DecodeFailureHandler]] used.
  *
  * If `e2` is shadowed by `e1` it means that `e2` will be never called because all requests will be handled by `e1` beforehand. Examples
  * where `e2` is shadowed by `e1`:
  *
  * {{{
  * e1 = endpoint.get.in("x" / paths)
  * e2 = endpoint.get.in("x" / "y" / "x")
  *
  * e1 = endpoint.get.in(path[String].name("y_1") / path[String].name("y_2"))
  * e2 = endpoint.get.in(path[String].name("y_3") / path[String].name("y_4"))
  * }}}
  */
case class ShadowedEndpoint(e: AnyEndpoint, by: AnyEndpoint) {
  override def toString: String = e.input.show + ", is shadowed by: " + by.input.show
}
