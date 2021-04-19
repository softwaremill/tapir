package sttp.tapir.server.interceptor.content

import sttp.model.{ContentTypeRange, StatusCode}
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.internal._
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._

/** If no body in the endpoint's outputs satisfies the constraints from the request's `Accept` header, returns
  * an empty response with status code 415, before any further processing (running the business logic) is done.
  */
class UnsupportedMediaTypeInterceptor[F[_], B] extends EndpointInterceptor[F, B] {

  override def apply(responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I])(implicit monad: MonadError[F]): F[ServerResponse[B]] = {
        ctx.request.acceptsContentTypes match {
          case _ @(Right(Nil) | Right(ContentTypeRange.AnyRange :: Nil)) => endpointHandler.onDecodeSuccess(ctx)
          case Right(ranges) =>
            val hasMatchingRepresentation = ctx.endpoint.output.supportedMediaTypes.exists(mt => ranges.exists(mt.matches))

            if (hasMatchingRepresentation) endpointHandler.onDecodeSuccess(ctx)
            else responder(ctx.request, ValuedEndpointOutput(statusCode(StatusCode.UnsupportedMediaType), ()))

          case Left(_) =>
            // we're forgiving, if we can't parse the accepts header, we try to return any response
            endpointHandler.onDecodeSuccess(ctx)
        }
      }

      override def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] =
        endpointHandler.onDecodeFailure(ctx)
    }
}
