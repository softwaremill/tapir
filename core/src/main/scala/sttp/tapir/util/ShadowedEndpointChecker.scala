package sttp.tapir.util

import sttp.tapir.{Endpoint, ShadowedEndpoint}

object ShadowedEndpointChecker {

  def findShadowedEndpoints(endpoints: List[Endpoint[_, _, _, _]]): List[ShadowedEndpoint] = {
    val duplicates = endpoints
      .groupBy(endpoint => extractNormalizedPath(endpoint))
      .filter(duplicate =>
        duplicate match {
          case (_, duplicatedEndpoints) => duplicatedEndpoints.size > 1
        }
      )
    if (duplicates.isEmpty) List() else buildShadowedEndpoints(duplicates)
  }

  def buildShadowedEndpoints(g: Map[String, List[Endpoint[_, _, _, _]]]): List[ShadowedEndpoint] = {
    g.flatMap({
      case (_, overlappingEndpoint :: overlappedEndpoints) =>
        overlappedEndpoints.map(e => ShadowedEndpoint(e, overlappingEndpoint))
      case (_, Nil) => Nil
    }).toList
  }

  def extractNormalizedPath(i: Endpoint[_, _, _, _]): String = {
    val pathWithoutWildcard = i.input.show.replace("/*", "")
    if (pathWithoutWildcard.endsWith("/")) pathWithoutWildcard.dropRight(1) else pathWithoutWildcard
  }
}
