package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest

trait RequestHandler[F[_], B] {
  def apply(request: ServerRequest)(implicit monad: MonadError[F]): F[ServerInterpreterResult[B]]
}

object RequestHandler {
  def from[F[_], B](
      f: (ServerRequest, MonadError[F]) => F[ServerInterpreterResult[B]]
  ): RequestHandler[F, B] = new RequestHandler[F, B] {
    override def apply(request: ServerRequest)(implicit
        monad: MonadError[F]
    ): F[ServerInterpreterResult[B]] = f(request, monad)
  }
}
