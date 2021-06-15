package sttp.tapir.util

import sttp.model.Method
import sttp.tapir.internal.RichEndpointInput
import sttp.tapir.{Endpoint, EndpointInput, ShadowedEndpoint}

import java.net.URLEncoder

object ShadowedEndpointChecker {

  def apply(endpoints: List[Endpoint[_, _, _, _]]): List[ShadowedEndpoint] = {
    findShadowedEndpoints(endpoints, List()).distinctBy(_.e)
  }

  private def findShadowedEndpoints(endpoints: List[Endpoint[_, _, _, _]], acc: List[ShadowedEndpoint]): List[ShadowedEndpoint] =
    endpoints match {
      case endpoint :: endpoints => findShadowedEndpoints(endpoints, acc ::: findAllShadowedByEndpoint(endpoint, endpoints))
      case Nil => acc
    }

  private def findAllShadowedByEndpoint(endpoint: Endpoint[_, _, _, _], in: List[Endpoint[_, _, _, _]]): List[ShadowedEndpoint] = {
    in.filter(e => checkIfShadows(endpoint, e)).map(e => ShadowedEndpoint(e, endpoint))
  }

  private def checkIfShadows(e1: Endpoint[_, _, _, _], e2: Endpoint[_, _, _, _]): Boolean = {
    val e1Segments = extractSegments(e1)
    val e2Segments = extractSegments(e2)
    val commonSegments = e1Segments
      .zip(e2Segments)
      .filter(p => p._1.equals(WildcardPathSegment) || p._1.equals(p._2) || p._1.equals(PathVariableSegment))

    if (e1Segments.size == commonSegments.size && e1Segments.size == e2Segments.size) true
    else if (e1Segments.size == commonSegments.size && e1Segments.last.equals(WildcardPathSegment)) true
    else false
  }

  private def extractSegments(endpoint: Endpoint[_, _, _, _]): Vector[PathComponent] = {
    endpoint.input.traverseInputs({
      case EndpointInput.FixedPath(x, _, _) => Vector(FixedPathSegment(URLEncoder.encode(x, "UTF-8")))
      case EndpointInput.PathsCapture(_, _) => Vector(WildcardPathSegment)
      case EndpointInput.PathCapture(_, _, _) => Vector(PathVariableSegment)
      case EndpointInput.FixedMethod(m, _, _) => Vector(FixedMethodComponent(m))
    })
  }
}

private sealed trait PathComponent

private case object PathVariableSegment extends PathComponent

private case object WildcardPathSegment extends PathComponent

private case class FixedPathSegment(s: String) extends PathComponent

private case class FixedMethodComponent(m: Method) extends PathComponent