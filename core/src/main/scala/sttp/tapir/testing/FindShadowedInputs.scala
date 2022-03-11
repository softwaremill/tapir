package sttp.tapir.testing

import sttp.tapir.internal.{RichEndpointInput, UrlencodedData}
import sttp.tapir.{AnyEndpoint, EndpointInput}

object FindShadowedInputs {
  def apply(endpoint: AnyEndpoint): Option[String] = {
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

  private def inputPathSegments(input: EndpointInput[_]): Vector[PathComponent] = {
    input
      .traverseInputs({
        case EndpointInput.FixedPath(x, _, _)   => Vector(FixedPathSegment(UrlencodedData.encode(x)))
        case EndpointInput.PathsCapture(_, _)   => Vector(WildcardPathSegment)
        case EndpointInput.PathCapture(_, _, _) => Vector(PathVariableSegment)
      })
  }
}
