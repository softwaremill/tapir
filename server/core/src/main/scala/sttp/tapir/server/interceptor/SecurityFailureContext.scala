package sttp.tapir.server.interceptor

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint

case class SecurityFailureContext[F[_], A](
    serverEndpoint: ServerEndpoint.Full[A, ?, ?, ?, ?, ?, F],
    securityInput: A,
    request: ServerRequest
) {
  def endpoint: Endpoint[A, ?, ?, ?, ?] = serverEndpoint.endpoint
}
