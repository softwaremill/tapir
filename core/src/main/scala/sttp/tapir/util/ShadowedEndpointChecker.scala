package sttp.tapir.util

import sttp.tapir.{Endpoint, ShadowedEndpoint}

object ShadowedEndpointChecker {
  final val WILDCARD_SYMBOL = "/..."
  final val PATH_VARIABLE_REPLACEMENT_SYMBOL = "_"
  final val PATH_VARIABLE_SEGMENT = "\\[(.*?)]"

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
    val commonSegments = e1Segments.zip(e2Segments).filter(p => p._1.equals(p._2) || p._1.equals("/_") || p._2.equals("/_"))

    if (e1Segments.size == commonSegments.size && e1Segments.size == e2Segments.size) true
    else if (e1Segments.size == commonSegments.size && endsWithWildcard(e1)) true
    else if (e2Segments.size == commonSegments.size && endsWithWildcard(e2)) true
    else false
  }

  private def extractSegments(endpoint: Endpoint[_, _, _, _]): List[String] = {
    normalizePath(endpoint).split(' ').toList
  }

  private def endsWithWildcard(e: Endpoint[_, _, _, _]): Boolean = {
    e.input.show.endsWith(WILDCARD_SYMBOL)
  }

  private def normalizePath(e: Endpoint[_, _, _, _]): String = {
    e.input.show
      .replaceAll(PATH_VARIABLE_SEGMENT, PATH_VARIABLE_REPLACEMENT_SYMBOL)
      .replace(WILDCARD_SYMBOL, "")
      .split('?').head
  }
}