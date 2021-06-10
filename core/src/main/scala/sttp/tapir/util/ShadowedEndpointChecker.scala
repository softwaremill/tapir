package sttp.tapir.util

import sttp.tapir.{Endpoint, ShadowedEndpoint}

object ShadowedEndpointChecker {

  def findShadowedEndpoints(endpoints: List[Endpoint[_, _, _, _]]): List[ShadowedEndpoint] = {
    def findEndpoints(endpoints: List[Endpoint[_, _, _, _]], acc: List[ShadowedEndpoint]): List[ShadowedEndpoint] =
      endpoints match {
        case endpoint :: endpoints => findEndpoints(endpoints, acc ::: findAllShadowedByEndpoint(endpoint, endpoints))
        case Nil => acc
      }

    def findAllShadowedByEndpoint(endpoint: Endpoint[_, _, _, _], endpoints: List[Endpoint[_, _, _, _]]): List[ShadowedEndpoint] = {
      if (hasWildcard(endpoint))
        endpoints.filter(e => normalizePath(e).startsWith(normalizePath(endpoint))).map(e => ShadowedEndpoint(e, endpoint))
      else
        endpoints.filter(e => normalizePath(e).equals(normalizePath(endpoint))).map(e => ShadowedEndpoint(e, endpoint))
    }

    findEndpoints(endpoints, List()).distinctBy(_.e)
  }

  def hasWildcard(e: Endpoint[_, _, _, _]): Boolean = {
    e.input.show.endsWith("/...")
  }

  def normalizePath(e: Endpoint[_, _, _, _]): String = {
    val pathWithoutWildcard = e.input.show.replace(" /...", "")
    if (pathWithoutWildcard.endsWith("/")) pathWithoutWildcard.dropRight(1) else pathWithoutWildcard
  }
}
