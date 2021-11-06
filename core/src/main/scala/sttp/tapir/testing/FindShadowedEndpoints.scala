package sttp.tapir.testing

import sttp.tapir.internal.{RichEndpointInput, UrlencodedData}
import sttp.tapir.{AnyEndpoint, EndpointInput, testing}

import scala.annotation.tailrec

object FindShadowedEndpoints {
  def apply(endpoints: List[AnyEndpoint]): Set[ShadowedEndpoint] = {
    findShadowedEndpoints(endpoints, List()).groupBy(_.e).map(_._2.head).toSet
  }

  @tailrec
  private def findShadowedEndpoints(endpoints: List[AnyEndpoint], acc: List[ShadowedEndpoint]): List[ShadowedEndpoint] =
    endpoints match {
      case endpoint :: endpoints => findShadowedEndpoints(endpoints, acc ::: findAllShadowedByEndpoint(endpoint, endpoints))
      case Nil                   => acc
    }

  private def findAllShadowedByEndpoint(endpoint: AnyEndpoint, in: List[AnyEndpoint]): List[ShadowedEndpoint] = {
    in.filter(e => checkIfShadows(endpoint, e)).map(e => testing.ShadowedEndpoint(e, endpoint))
  }

  private def checkIfShadows(e1: AnyEndpoint, e2: AnyEndpoint): Boolean =
    checkMethods(e1, e2) && checkPaths(e1, e2)

  private def checkMethods(e1: AnyEndpoint, e2: AnyEndpoint): Boolean =
    e1.httpMethod.equals(e2.httpMethod) || e1.httpMethod.isEmpty

  private def checkPaths(e1: AnyEndpoint, e2: AnyEndpoint): Boolean = {
    val e1Segments = extractPathSegments(e1)
    val e2Segments = extractPathSegments(e2)
    val commonSegments = e1Segments
      .zip(e2Segments)
      .filter(p => p._1.equals(WildcardPathSegment) || p._1.equals(p._2) || p._1.equals(PathVariableSegment))

    if (e1Segments.size == commonSegments.size && e1Segments.size == e2Segments.size) true
    else if (e1Segments.size == commonSegments.size && endsWithWildcard(e1Segments)) true
    else if (e1Segments.size - 1 == commonSegments.size && e2Segments.size == commonSegments.size && endsWithWildcard(e1Segments)) true
    else false
  }

  private def endsWithWildcard(paths: Vector[PathComponent]): Boolean = {
    paths.nonEmpty && paths.indexOf(WildcardPathSegment) == paths.size - 1
  }

  private def extractPathSegments(endpoint: AnyEndpoint): Vector[PathComponent] = {
    endpoint.securityInput
      .and(endpoint.input)
      .traverseInputs({
        case EndpointInput.FixedPath(x, _, _)   => Vector(FixedPathSegment(UrlencodedData.encode(x)))
        case EndpointInput.PathsCapture(_, _)   => Vector(WildcardPathSegment)
        case EndpointInput.PathCapture(_, _, _) => Vector(PathVariableSegment)
      })
  }
}

private sealed trait PathComponent
private case object PathVariableSegment extends PathComponent
private case object WildcardPathSegment extends PathComponent
private case class FixedPathSegment(s: String) extends PathComponent
