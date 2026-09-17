package sttp.tapir.server.interpreter

import sttp.tapir.server.{EndpointBodyVerifier, ServerEndpoint}

/** Verifies that the given endpoints can be served, throwing if any of them can't, and returns the request-to-endpoints function which
  * [[ServerInterpreter]] needs.
  *
  * Server interpreters should call this when constructing routes.
  */
object PrepareServerEndpoints {
  def apply[R, F[_]](serverEndpoints: List[ServerEndpoint[R, F]]): FilterServerEndpoints[R, F] = {
    EndpointBodyVerifier.throwOnErrors(EndpointBodyVerifier.verify(serverEndpoints.map(_.endpoint)))
    FilterServerEndpoints(serverEndpoints)
  }
}
