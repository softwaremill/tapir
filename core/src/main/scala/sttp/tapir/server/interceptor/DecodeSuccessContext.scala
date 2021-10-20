package sttp.tapir.server.interceptor

import sttp.tapir.Endpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint

case class DecodeSuccessContext[F[_], I](
    serverEndpoint: ServerEndpoint[I, _, _, _, F],
    i: I,
    request: ServerRequest
) {
  def endpoint: Endpoint[I, _, _, _] = serverEndpoint.endpoint
}

object DecodeSuccessContext {
  def fromAnyInput[F[_], I](serverEndpoint: ServerEndpoint[I, _, _, _, F], i: Any, request: ServerRequest): DecodeSuccessContext[F, I] =
    DecodeSuccessContext(serverEndpoint, i.asInstanceOf[I], request)
}
