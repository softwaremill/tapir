package sttp.tapir.util

import sttp.tapir.{Endpoint, ShadowedEndpoint}

object ShadowedEndpointChecker {

  def findShadowedEndpoints(e: List[Endpoint[_, _, _, _]]): Option[ShadowedEndpoint[_,_,_,_]] = {
    Option.empty
  }
}
