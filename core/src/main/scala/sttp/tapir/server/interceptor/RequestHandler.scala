package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interpreter.DecodeBasicInputsResult

trait RequestHandler[F[_], B] {
  def apply(request: ServerRequest)(implicit monad: MonadError[F]): F[Either[List[DecodeBasicInputsResult.Failure], ServerResponse[B]]]
}

object RequestHandler {
  def from[F[_], B](
      f: (ServerRequest, MonadError[F]) => F[Either[List[DecodeBasicInputsResult.Failure], ServerResponse[B]]]
  ): RequestHandler[F, B] = new RequestHandler[F, B] {
    override def apply(request: ServerRequest)(implicit
        monad: MonadError[F]
    ): F[Either[List[DecodeBasicInputsResult.Failure], ServerResponse[B]]] = f(request, monad)
  }
}
