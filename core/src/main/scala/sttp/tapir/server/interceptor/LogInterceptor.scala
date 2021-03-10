package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.{DecodeFailureContext, LogRequestHandling}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

class LogInterceptor[T, F[_], B](handling: LogRequestHandling[T], toF: (T, ServerRequest) => F[Unit]) extends EndpointInterceptor[F, B] {
  override def onDecodeSuccess[I](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[B]] = {
    next(None)
      .flatMap { response =>
        toF(handling.requestHandled(endpoint, response.code.code), request).map(_ => response)
      }
      .handleError { case e: Exception =>
        toF(handling.logicException(endpoint, e), request).flatMap(_ => monad.error(e))
      }
  }

  override def onDecodeFailure(
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] = {
    next(None).flatMap {
      case r @ None =>
        toF(handling.decodeFailureNotHandled(endpoint, DecodeFailureContext(failingInput, failure, endpoint)), request).map(_ => r)
      case r @ Some(response) =>
        toF(handling.decodeFailureHandled(endpoint, DecodeFailureContext(failingInput, failure, endpoint), response), request).map(_ => r)
    }
  }
}
