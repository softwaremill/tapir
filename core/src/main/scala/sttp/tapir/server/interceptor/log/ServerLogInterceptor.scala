package sttp.tapir.server.interceptor.log

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

/** @tparam F The effect in which log messages are returned. */
class ServerLogInterceptor[F[_]](log: ServerLog[F]) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[U, I](ctx: DecodeSuccessContext[F, U, I])(implicit
          monad: MonadError[F],
          bodyListener: BodyListener[F, B]
      ): F[ServerResponse[B]] = {
        decodeHandler
          .onDecodeSuccess(ctx)
          .flatMap { response =>
            log.requestHandled(ctx, response).map(_ => response)
          }
          .handleError { case e: Throwable =>
            log.exception(ctx.endpoint, ctx.request, e).flatMap(_ => monad.error(e))
          }
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        decodeHandler
          .onSecurityFailure(ctx)
          .flatMap { response =>
            log.securityFailureHandled(ctx, response).map(_ => response)
          }
          .handleError { case e: Throwable =>
            log.exception(ctx.endpoint, ctx.request, e).flatMap(_ => monad.error(e))
          }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        decodeHandler
          .onDecodeFailure(ctx)
          .flatMap {
            case r @ None =>
              log.decodeFailureNotHandled(ctx).map(_ => r: Option[ServerResponse[B]])
            case r @ Some(response) =>
              monad
                .unit(log.decodeFailureHandled(ctx, response))
                .map(_ => r: Option[ServerResponse[B]])
          }
          .handleError { case e: Throwable =>
            log.exception(ctx.endpoint, ctx.request, e).flatMap(_ => monad.error(e))
          }
      }
    }
}
