package sttp.tapir.util

import sttp.tapir.{Endpoint, ShadowedEndpoint}

object ShadowedEndpointChecker {

  def findShadowedEndpoints(endpoints: List[Endpoint[_, _, _, _]]): List[ShadowedEndpoint] = {
    val duplicates = endpoints.groupBy(x => normalizePath(x.input.show)).filter(c => c._2.size > 1)
    if (duplicates.isEmpty) List() else buildShadowedEndpoints(duplicates)
  }

  def buildShadowedEndpoints(g: Map[String, List[Endpoint[_, _, _, _]]]): List[ShadowedEndpoint] = {
    g.flatMap({
      case (_,overlappingEndpoint::overlappedEndpoints) =>
        overlappedEndpoints.map(e => ShadowedEndpoint(e, overlappingEndpoint))
      case (_,Nil) => Nil
    }).toList
  }

  def normalizePath(i: String): String = {
    val withoutWildcard = i.replace("/*", "")
    if (withoutWildcard.endsWith("/")) withoutWildcard.dropRight(1) else withoutWildcard
  }
}
