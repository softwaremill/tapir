package sttp.tapir.server.interceptor.log

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.DecodeFailureContext
import sttp.tapir.server.interceptor.{EndpointInterceptor, ValuedEndpointOutput}
import sttp.tapir.{DecodeResult, Endpoint, EndpointInput}

class ServerLogInterceptor[T, F[_], B](log: ServerLog[T], toEffect: (T, ServerRequest) => F[Unit]) extends EndpointInterceptor[F, B] {
  override def onDecodeSuccess[I](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[B]] = {
    next(None)
      .flatMap { response =>
        toEffect(log.requestHandled(endpoint, response.code.code), request).map(_ => response)
      }
      .handleError { case e: Exception =>
        toEffect(log.exception(endpoint, e), request).flatMap(_ => monad.error(e))
      }
  }

  override def onDecodeFailure(
      request: ServerRequest,
      endpoint: Endpoint[_, _, _, _],
      failure: DecodeResult.Failure,
      failingInput: EndpointInput[_],
      next: Option[ValuedEndpointOutput[_]] => F[Option[ServerResponse[B]]]
  )(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] = {
    next(None)
      .flatMap {
        case r @ None =>
          toEffect(log.decodeFailureNotHandled(endpoint, DecodeFailureContext(failingInput, failure, endpoint)), request).map(_ =>
            r: Option[ServerResponse[B]]
          )
        case r @ Some(response) =>
          toEffect(log.decodeFailureHandled(endpoint, DecodeFailureContext(failingInput, failure, endpoint), response), request).map(_ =>
            r: Option[ServerResponse[B]]
          )
      }
      .handleError { case e: Exception =>
        toEffect(log.exception(endpoint, e), request).flatMap(_ => monad.error(e))
      }
  }
}
