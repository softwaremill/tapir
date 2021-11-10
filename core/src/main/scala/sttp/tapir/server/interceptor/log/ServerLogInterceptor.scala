package sttp.tapir.server.interceptor.log

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

/** @tparam T
  *   Interpreter-specific value representing the log effect.
  */
class ServerLogInterceptor[F[_]](log: ServerLog) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[U, I](ctx: DecodeSuccessContext[F, U, I])(implicit
          monad: MonadError[F],
          bodyListener: BodyListener[F, B]
      ): F[ServerResponse[B]] = {
        decodeHandler
          .onDecodeSuccess(ctx)
          .flatMap { response =>
            monad.unit(log.requestHandled(ctx.endpoint, ctx.request, response)).map(_ => response)
          }
          .handleError { case e: Throwable =>
            monad.unit(log.exception(ctx.endpoint, ctx.request, e)).flatMap(_ => monad.error(e))
          }
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        decodeHandler
          .onSecurityFailure(ctx)
          .flatMap { response =>
            monad.unit(log.securityFailureHandled(ctx.endpoint, ctx.request, response)).map(_ => response)
          }
          .handleError { case e: Throwable =>
            monad.unit(log.exception(ctx.endpoint, ctx.request, e)).flatMap(_ => monad.error(e))
          }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        decodeHandler
          .onDecodeFailure(ctx)
          .flatMap {
            case r @ None =>
              monad.unit(log.decodeFailureNotHandled(ctx)).map(_ => r: Option[ServerResponse[B]])
            case r @ Some(response) =>
              monad
                .unit(log.decodeFailureHandled(ctx, response))
                .map(_ => r: Option[ServerResponse[B]])
          }
          .handleError { case e: Throwable =>
            monad.unit(log.exception(ctx.endpoint, ctx.request, e)).flatMap(_ => monad.error(e))
          }
      }
    }
}
