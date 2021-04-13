package sttp.tapir.server.interceptor

import sttp.tapir.model.{ServerRequest, ServerResponse}

trait RequestHandler[F[_], B] extends (ServerRequest => F[Option[ServerResponse[B]]]) {
  def apply(request: ServerRequest): F[Option[ServerResponse[B]]]
}
