package sttp.tapir.testing

import sttp.tapir.internal.RichEndpointInput
import sttp.tapir.testing.EndpointVerificationError.showMethodWithFullPath
import sttp.tapir.{AnyEndpoint, EndpointInput}

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
  override def toString: String = showMethodWithFullPath(e) + ", is shadowed by: " + showMethodWithFullPath(by)
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
  override def toString: String = s"A wildcard pattern in ${showMethodWithFullPath(e)} shadows the rest of the paths at index $at"
}

private[testing] object EndpointVerificationError {
  private[testing] def showMethodWithFullPath(e: AnyEndpoint) = {
    val fullInput = e.securityInput.and(e.input)

    val fullInputPath = fullInput
      .traverseInputs {
        case a @ EndpointInput.FixedPath(_, _, _)   => Vector(a)
        case b @ EndpointInput.PathsCapture(_, _)   => Vector(b)
        case c @ EndpointInput.PathCapture(_, _, _) => Vector(c)
      }
    val finalPathShow = fullInputPath.map(_.show).mkString(" ")

    fullInput.method.map(_.method + " " + finalPathShow).getOrElse(finalPathShow)
  }
}
