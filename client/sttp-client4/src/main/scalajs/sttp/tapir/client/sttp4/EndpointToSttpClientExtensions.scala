package sttp.tapir.client.sttp4

import sttp.model.StatusCode

private[sttp4] trait EndpointToSttpClientExtensions { this: EndpointToSttpClient[_] =>

  /** This needs to be platform-specific due to #2663, as on JS we don't get access to the 101 status code. */
  val webSocketSuccessStatusCode: StatusCode = StatusCode.Ok
}
