package sttp.tapir.testing

import sttp.tapir.AnyEndpoint

class EndpointVerificationError {
  def showAllPaths(e: AnyEndpoint) = {
    val secPathsLen = EndpointVerifier.inputPathSegments(e.securityInput).length

    if (secPathsLen > 0) {
      e.input.show.patch(4, e.securityInput.show + " ", 0)
    } else {
      e.input.show
    }
  }
}

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
case class ShadowedEndpointError(e: AnyEndpoint, by: AnyEndpoint) extends EndpointVerificationError {
  override def toString: String = showAllPaths(e) + ", is shadowed by: " + by.input.show
}

case class IncorrectPathsError(e: AnyEndpoint, at: Int) extends EndpointVerificationError {
  override def toString: String = s"A wildcard pattern in ${showAllPaths(e)} shadows the rest of the paths at index $at"
}
