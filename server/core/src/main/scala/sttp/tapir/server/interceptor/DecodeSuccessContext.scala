package sttp.tapir.server.interceptor

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint

case class DecodeSuccessContext[F[_], A, U, I](
    serverEndpoint: ServerEndpoint.Full[A, U, I, _, _, _, F],
    securityInput: A,
    principal: U,
    input: I,
    request: ServerRequest
) {
  def endpoint: Endpoint[A, I, _, _, _] = serverEndpoint.endpoint
}
