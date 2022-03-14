package sttp.tapir.testing

import sttp.tapir.internal.{RichEndpointInput, UrlencodedData}
import sttp.tapir.{AnyEndpoint, EndpointInput}

object EndpointVerifier {
  def apply(endpoint: AnyEndpoint): Option[String] = {
    val secShadowing = findSecurityShadowing(endpoint)
    secShadowing match {
      case None => findWildcardShadowing(endpoint)
      case _ => secShadowing
    }
  }

  def findSecurityShadowing(endpoint: AnyEndpoint): Option[String] = {
    val paths = inputPathSegments(endpoint.securityInput)
      .zip(
        inputPathSegments(endpoint.input)
      )
      .takeWhile(p => p._1.equals(WildcardPathSegment) || p._1.equals(p._2) || p._1.equals(PathVariableSegment))

    if (paths.length != 0) {
      Some(endpoint.input.show + ", is shadowed by: " + endpoint.securityInput.show)
    } else {
      None
    }
  }

  def findWildcardShadowing(endpoint: AnyEndpoint): Option[String] = {
    val secPaths = inputPathSegments(endpoint.securityInput)
    val paths = inputPathSegments(endpoint.input)

    if (secPaths.indexOf(WildcardPathSegment) != secPaths.length - 1) {
      Some(s"WildcardPathSegment /* at index ${secPaths.indexOf(WildcardPathSegment)} shadows the rest of the path")
    } else if (paths.indexOf(WildcardPathSegment) != paths.length - 1) {
      Some(s"WildcardPathSegment /* at index ${paths.indexOf(WildcardPathSegment)} shadows the rest of the path")
    } else {
      None
    }
  }

  private def inputPathSegments(input: EndpointInput[_]): Vector[PathComponent] = {
    input
      .traverseInputs({
        case EndpointInput.FixedPath(x, _, _)   => Vector(FixedPathSegment(UrlencodedData.encode(x)))
        case EndpointInput.PathsCapture(_, _)   => Vector(WildcardPathSegment)
        case EndpointInput.PathCapture(_, _, _) => Vector(PathVariableSegment)
      })
  }
}
