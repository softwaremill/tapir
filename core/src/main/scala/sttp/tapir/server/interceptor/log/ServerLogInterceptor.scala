package sttp.tapir.server.interceptor.log

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

/** @param toEffect
  *   Converts the interpreter-specific value representing the log effect, into an `F`-effect, which can be composed with the result of
  *   processing a request.
  * @tparam T
  *   Interpreter-specific value representing the log effect.
  */
class ServerLogInterceptor[T, F[_]](log: ServerLog[T], toEffect: (T, ServerRequest) => F[Unit]) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[U, I](ctx: DecodeSuccessContext[F, U, I])(implicit
          monad: MonadError[F],
          bodyListener: BodyListener[F, B]
      ): F[ServerResponse[B]] = {
        decodeHandler
          .onDecodeSuccess(ctx)
          .flatMap { response =>
            toEffect(log.requestHandled(ctx.endpoint, response.code.code), ctx.request).map(_ => response)
          }
          .handleError { case e: Throwable =>
            toEffect(log.exception(ctx.endpoint, e), ctx.request).flatMap(_ => monad.error(e))
          }
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        decodeHandler
          .onSecurityFailure(ctx)
          .flatMap { response =>
            toEffect(log.securityFailureHandled(ctx.endpoint, response), ctx.request).map(_ => response)
          }
          .handleError { case e: Throwable =>
            toEffect(log.exception(ctx.endpoint, e), ctx.request).flatMap(_ => monad.error(e))
          }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        decodeHandler
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
