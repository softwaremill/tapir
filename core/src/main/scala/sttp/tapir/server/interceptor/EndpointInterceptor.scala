package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

trait EndpointInterceptor[F[_]] {
  def onDecodeSuccess[I, WB, B](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[WB, B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[WB, B]] = next(None)

  def onDecodeFailure[WB, B](
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[WB, B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[WB, B]]] = next(None)
}
