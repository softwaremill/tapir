package sttp.tapir.server.interceptor.content

import sttp.model.{ContentTypeRange, StatusCode}
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.internal._
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

/** If no body in the endpoint's outputs satisfies the constraints from the request's `Accept` header, returns an empty response with status
  * code 415, before any further processing (running the business logic) is done.
  */
class UnsupportedMediaTypeInterceptor[F[_]] extends EndpointInterceptor[F] {

  override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        ctx.request.acceptsContentTypes match {
          case _ @(Right(Nil) | Right(ContentTypeRange.AnyRange :: Nil)) => endpointHandler.onDecodeSuccess(ctx)
          case Right(ranges) =>
            val supportedMediaTypes = ctx.endpoint.output.supportedMediaTypes
            // empty supported media types -> no body is defined, so the accepts header can be ignored
            val hasMatchingRepresentation = supportedMediaTypes.exists(mt => ranges.exists(mt.matches)) || supportedMediaTypes.isEmpty

            if (hasMatchingRepresentation) endpointHandler.onDecodeSuccess(ctx)
            else responder(ctx.request, ValuedEndpointOutput(statusCode(StatusCode.UnsupportedMediaType), ()))

          case Left(_) =>
            // we're forgiving, if we can't parse the accepts header, we try to return any response
            endpointHandler.onDecodeSuccess(ctx)
        }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] =
        endpointHandler.onDecodeFailure(ctx)
    }
}
