package sttp.tapir.server.interceptor.decodefailure

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.{DecodeFailureContext, EndpointInterceptor, ValuedEndpointOutput}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

class DecodeFailureInterceptor[F[_], B](handler: DecodeFailureHandler) extends EndpointInterceptor[F, B] {
  override def onDecodeFailure(
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] = {
    handler(DecodeFailureContext(failingInput, failure, endpoint, request)) match {
      case None               => next(None)
      case Some(valuedOutput) => next(Some(valuedOutput))
    }
  }
}
