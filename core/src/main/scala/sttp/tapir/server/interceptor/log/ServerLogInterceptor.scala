package sttp.tapir.server.interceptor.log

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

/** @param toEffect
  *   Converts the interpreter-specific value representing the log effect, into an `F`-effect, which can be composed with the result of
  *   processing a request.
  * @tparam T
  *   Interpreter-specific value representing the log effect.
  */
class ServerLogInterceptor[T, F[_]](log: ServerLog[T], toEffect: (T, ServerRequest) => F[Unit]) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def beforeDecode(request: ServerRequest, serverEndpoint: ServerEndpoint[_, _, _, _, F])(implicit
          monad: MonadError[F]
      ): F[BeforeDecodeResult[F, B]] =
        endpointHandler.beforeDecode(request, serverEndpoint)

      override def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I])(implicit
          monad: MonadError[F],
          bodyListener: BodyListener[F, B]
      ): F[ServerResponse[B]] = {
        endpointHandler
          .onDecodeSuccess(ctx)
          .flatMap { response =>
            toEffect(log.requestHandled(ctx.endpoint, response.code.code), ctx.request).map(_ => response)
          }
          .handleError { case e: Throwable =>
            toEffect(log.exception(ctx.endpoint, e), ctx.request).flatMap(_ => monad.error(e))
          }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        endpointHandler
          .onDecodeFailure(ctx)
          .flatMap {
            case r @ None =>
              toEffect(log.decodeFailureNotHandled(ctx), ctx.request).map(_ => r: Option[ServerResponse[B]])
            case r @ Some(response) =>
              toEffect(log.decodeFailureHandled(ctx, response), ctx.request)
                .map(_ => r: Option[ServerResponse[B]])
          }
          .handleError { case e: Throwable =>
            toEffect(log.exception(ctx.endpoint, e), ctx.request).flatMap(_ => monad.error(e))
          }
      }
    }
}
