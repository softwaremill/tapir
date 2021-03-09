package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.{DecodeFailureContext, DecodeFailureHandler, DecodeFailureHandling, interceptor}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

class DecodeFailureInterceptor[F[_]](handler: DecodeFailureHandler) extends EndpointInterceptor[F] {
  override def onDecodeFailure[WB, B](
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[WB, B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[WB, B]]] = {
    handler(DecodeFailureContext(failingInput, failure, endpoint)) match {
      case DecodeFailureHandling.NoMatch                            => next(None)
      case DecodeFailureHandling.RespondWithResponse(output, value) => next(Some(interceptor.ValuedEndpointOutput(output, value)))
    }
  }
}
