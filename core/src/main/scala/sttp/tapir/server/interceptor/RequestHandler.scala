package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}

trait RequestHandler[F[_], B] {
  def apply(request: ServerRequest)(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]]
}
