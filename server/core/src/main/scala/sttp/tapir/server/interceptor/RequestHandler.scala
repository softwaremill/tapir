package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint

trait RequestHandler[F[_], R, B] {
  def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit monad: MonadError[F]): F[RequestResult[B]]
}

object RequestHandler {
  def from[F[_], R, B](f: (ServerRequest, List[ServerEndpoint[R, F]], MonadError[F]) => F[RequestResult[B]]): RequestHandler[F, R, B] =
    new RequestHandler[F, R, B] {
      override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
          monad: MonadError[F]
      ): F[RequestResult[B]] = f(request, endpoints, monad)
    }
}
