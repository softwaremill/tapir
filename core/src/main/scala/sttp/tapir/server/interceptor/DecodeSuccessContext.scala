package sttp.tapir.server.interceptor

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint

case class DecodeSuccessContext[F[_], U, I](
    serverEndpoint: ServerEndpoint.Full[_, U, I, _, _, _, F],
    securityLogicResult: U,
    decodedInput: I,
    request: ServerRequest
) {
  def endpoint: Endpoint[_, I, _, _, _] = serverEndpoint.endpoint
}
