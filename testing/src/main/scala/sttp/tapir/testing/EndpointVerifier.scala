package sttp.tapir.testing

import sttp.model.Method
import sttp.model.StatusCode.{NoContent, NotModified}
import sttp.tapir.internal.{RichEndpointInput, RichEndpointOutput, UrlencodedData}
import sttp.tapir.{AnyEndpoint, EndpointInput, testing}

import scala.annotation.tailrec
import sttp.tapir.AnyEndpoint
import sttp.tapir.EndpointOutput
import sttp.tapir.EndpointOutput.StatusCode
import sttp.tapir.EndpointOutput.FixedStatusCode
import sttp.tapir.EndpointIO

object EndpointVerifier {
  def apply(endpoints: List[AnyEndpoint]): Set[EndpointVerificationError] = {
    findShadowedEndpoints(endpoints, List()).groupBy(_.e).map(_._2.head).toSet ++
      findIncorrectPaths(endpoints).toSet ++
      findDuplicatedMethodDefinitions(endpoints).toSet ++
      findForbiddenStatusAndBody(endpoints).toSet
  }

  private def findIncorrectPaths(endpoints: List[AnyEndpoint]): List[IncorrectPathsError] = {
    endpoints
      .map(e => {
        val paths = extractPathSegments(e)
        val wildCardIndex = paths.indexOf(WildcardPathSegment)
        (!List(paths.length - 1, -1).contains(wildCardIndex), IncorrectPathsError(e, wildCardIndex))
      })
      .filter(_._1)
      .map(_._2)
  }

  @tailrec
  private def findShadowedEndpoints(endpoints: List[AnyEndpoint], acc: List[ShadowedEndpointError]): List[ShadowedEndpointError] =
    endpoints match {
      case endpoint :: endpoints => findShadowedEndpoints(endpoints, acc ::: findAllShadowedByEndpoint(endpoint, endpoints))
      case Nil                   => acc
    }

  private def findAllShadowedByEndpoint(endpoint: AnyEndpoint, in: List[AnyEndpoint]): List[ShadowedEndpointError] = {
    in.filter(e => checkIfShadows(endpoint, e)).map(e => testing.ShadowedEndpointError(e, endpoint))
  }

  private def findForbiddenStatusAndBody(endpoints: List[AnyEndpoint]): List[UnexpectedBodyError] =
    endpoints.flatMap(hasStatusWithoutBody)

  private def checkIfShadows(e1: AnyEndpoint, e2: AnyEndpoint): Boolean =
    checkMethods(e1, e2) && checkPaths(e1, e2)

  private def checkMethods(e1: AnyEndpoint, e2: AnyEndpoint): Boolean =
    e1.method.equals(e2.method) || e1.method.isEmpty

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
    inputPathSegments(
      endpoint.securityInput
        .and(endpoint.input)
    )
  }

  private def hasStatusWithoutBody(endpoint: AnyEndpoint): Option[UnexpectedBodyError] = {
    (endpoint.output.asBasicOutputsList ++ endpoint.errorOutput.asBasicOutputsList)
      .collect { list =>
        list
          .collectFirst { case _: EndpointIO.Body[_, _] => list }
          .flatMap(_.collectFirst {
            case EndpointOutput.FixedStatusCode(NoContent, _, _)   => NoContent
            case EndpointOutput.FixedStatusCode(NotModified, _, _) => NotModified
          })
          .map(UnexpectedBodyError(endpoint, _))

      }
      .flatMap(_.toList)
      .headOption
  }

  private def inputPathSegments(input: EndpointInput[_]): Vector[PathComponent] = {
    input
      .traverseInputs({
        case EndpointInput.FixedPath(x, _, _)   => Vector(FixedPathSegment(UrlencodedData.encode(x)))
        case EndpointInput.PathsCapture(_, _)   => Vector(WildcardPathSegment)
        case EndpointInput.PathCapture(_, _, _) => Vector(PathVariableSegment)
      })
  }

  private def findDuplicatedMethodDefinitions(endpoints: List[AnyEndpoint]): List[EndpointVerificationError] = {
    endpoints
      .map { e => e -> inputDefinedMethods(e.input).toList }
      .filter(_._2.length > 1)
      .map { case (endpoint, methods) => DuplicatedMethodDefinitionError(endpoint, methods) }
  }

  private def inputDefinedMethods(input: EndpointInput[_]): Vector[Method] = {
    input.traverseInputs { case EndpointInput.FixedMethod(m, _, _) => Vector(m) }
  }
}

private sealed trait PathComponent
private case object PathVariableSegment extends PathComponent
private case object WildcardPathSegment extends PathComponent
private case class FixedPathSegment(s: String) extends PathComponent
