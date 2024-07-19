package sttp.tapir.testing

import sttp.model.Method
import sttp.tapir.AnyEndpoint
import sttp.model.StatusCode

sealed trait EndpointVerificationError

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
  override def toString: String = e.showShort + ", is shadowed by: " + by.showShort
}

/** Inputs in an endpoint are incorrect if a wildcard `paths` segment appears before any other segment. Reason: The wildcard `paths`
  * consumes all of the remaining input, so any segment after it will never be matched.
  *
  * Examples of incorrectly defined paths:
  *
  * {{{
  * endpoint.get.in("x" / paths / "y")
  * endpoint.get.in(paths / path[String].name("x")))
  * endpoint.get.securityIn(paths).in("x")
  * }}}
  */
case class IncorrectPathsError(e: AnyEndpoint, at: Int) extends EndpointVerificationError {
  override def toString: String = s"A wildcard pattern in ${e.showShort} shadows the rest of the paths at index $at"
}

/** Endpoint `e` has multiple methods definitions but should have at most 1 defined.
  *
  * Examples of incorrectly defined endpoints:
  *
  * {{{
  *   endpoint.get.post
  *   endpoint.post.in("x" / paths).get
  * }}}
  */
case class DuplicatedMethodDefinitionError(e: AnyEndpoint, methods: List[Method]) extends EndpointVerificationError {
  override def toString: String = s"An endpoint ${e.show} have multiple method definitions: $methods"
}

/** Endpoint `e` defines outputs where status code indicates no body, but at the same time a body output is specified. For status codes 204
  * and 304 it's forbidden by specification.
  *
  * Example of incorrectly defined endpoint:
  *
  * {{{
  *   endpoint.get.in("x").out(jsonBody[Unit]).out(statusCode(StatusCode.NoContent))
  * }}}
  */
case class UnexpectedBodyError(e: AnyEndpoint, statusCode: StatusCode) extends EndpointVerificationError {
  override def toString: String =
    s"An endpoint ${e.show} may return status code ${statusCode} with body, which is not allowed by specificiation."
}

/** The given name is used by multiple endpoints. This leads to duplicate operation ids being generated, when interpreting endpoints as
  * OpenAPI/AsyncAPI documentation. Which, in turn, causes incorrect behavior of code generators, which might silently drop endpoints.
  */
case class DuplicatedNameError(name: String) extends EndpointVerificationError {
  override def toString: String = s"Duplicate endpoints names found: $name"
}
