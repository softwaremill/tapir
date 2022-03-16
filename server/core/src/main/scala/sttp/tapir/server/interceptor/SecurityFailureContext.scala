package sttp.tapir.server.interceptor

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint

case class SecurityFailureContext[F[_], A](
    serverEndpoint: ServerEndpoint.Full[A, _, _, _, _, _, F],
    securityInput: A,
    request: ServerRequest
) {
  def endpoint: Endpoint[A, _, _, _, _] = serverEndpoint.endpoint
}
