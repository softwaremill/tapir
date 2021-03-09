package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.{DecodeFailureContext, LogRequestHandling}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

class LogInterceptor[T, F[_]](handling: LogRequestHandling[T], toF: (T, ServerRequest) => F[Unit]) extends EndpointInterceptor[F] {
  override def onDecodeSuccess[I, WB, B](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[WB, B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[WB, B]] = {
    next(None)
      .flatMap { response =>
        toF(handling.requestHandled(endpoint, response.code.code), request).map(_ => response)
      }
      .handleError { case e: Exception =>
        toF(handling.logicException(endpoint, e), request).flatMap(_ => monad.error(e))
      }
  }

  override def onDecodeFailure[WB, B](
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[WB, B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[WB, B]]] = {
    next(None).flatMap {
      case r @ None =>
        toF(handling.decodeFailureNotHandled(endpoint, DecodeFailureContext(failingInput, failure, endpoint)), request).map(_ => r)
      case r @ Some(response) =>
        toF(handling.decodeFailureHandled(endpoint, DecodeFailureContext(failingInput, failure, endpoint), response), request).map(_ => r)
    }
  }
}
