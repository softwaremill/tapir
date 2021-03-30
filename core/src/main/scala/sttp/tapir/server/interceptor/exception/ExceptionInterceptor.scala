package sttp.tapir.server.interceptor.exception

import sttp.monad.MonadError
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.{EndpointInterceptor, ValuedEndpointOutput}

class ExceptionInterceptor[F[_], B](handler: ExceptionHandler) extends EndpointInterceptor[F, B] {
  override def onDecodeSuccess[I](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[B]] = handle(request, endpoint, next)

  override def onDecodeFailure(
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] = handle(request, endpoint, next)

  private def handle[T](request: ServerRequest, endpoint: Endpoint[_, _, _, _], next: Option[ValuedEndpointOutput[_]] => F[T])(implicit
      monad: MonadError[F]
  ): F[T] = {
    monad.handleError(next(None)) { case e: Exception =>
      handler(ExceptionContext(e, endpoint, request)) match {
        case Some(value) => next(Some(value))
        case None        => monad.error(e)
      }
    }
  }
}
