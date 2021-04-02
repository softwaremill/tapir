package sttp.tapir.server.interceptor.content

import sttp.model.{ContentTypeRange, StatusCode}
import sttp.monad.MonadError
import sttp.tapir.internal._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.{EndpointInterceptor, ValuedEndpointOutput}
import sttp.tapir.{Endpoint, EndpointIO, StreamBodyIO, _}

/** If no body in the endpoint's outputs satisfies the constraints from the request's `Accept` header, returns
  * an empty response with status code 415, before any further processing (running the business logic) is done.
  */
class UnsupportedMediaTypeInterceptor[F[_], B] extends EndpointInterceptor[F, B] {
  override def onDecodeSuccess[I](
      request: ServerRequest,
      endpoint: Endpoint[I, _, _, _],
      i: I,
      next: Option[ValuedEndpointOutput[_]] => F[ServerResponse[B]]
  )(implicit monad: MonadError[F]): F[ServerResponse[B]] =
    request.acceptsContentTypes match {
      case _ @(Right(Nil) | Right(ContentTypeRange.AnyRange :: Nil)) => next(None)
      case Right(ranges) =>
        val hasMatchingRepresentation = endpoint.output.supportedMediaTypes.exists(mt => ranges.exists(mt.matches))

        if (hasMatchingRepresentation) next(None)
        else next(Some(ValuedEndpointOutput(statusCode(StatusCode.UnsupportedMediaType), ())))

      case Left(_) => next(Some(ValuedEndpointOutput(statusCode(StatusCode.BadRequest), ())))
    }
}
