package sttp.tapir.server.interceptor.exception

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

import scala.util.control.NonFatal

class ExceptionInterceptor[F[_]](handler: ExceptionHandler[F]) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[A, U, I](
          ctx: DecodeSuccessContext[F, A, U, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        monad.handleError(decodeHandler.onDecodeSuccess(ctx)) { case NonFatal(e) =>
          onException(e, ctx.endpoint, ctx.request)
        }
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        monad.handleError(decodeHandler.onSecurityFailure(ctx)) { case NonFatal(e) =>
          onException(e, ctx.endpoint, ctx.request)
        }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        monad.handleError(decodeHandler.onDecodeFailure(ctx)) { case NonFatal(e) =>
          onException(e, ctx.endpoint, ctx.request).map(Some(_))
        }
      }

      private def onException(e: Throwable, endpoint: AnyEndpoint, request: ServerRequest)(implicit
          monad: MonadError[F]
      ): F[ServerResponse[B]] =
        handler(ExceptionContext(e, endpoint, request)).flatMap {
          case Some(output) => responder(request, output)
          case None         => monad.error(e)
        }
    }
}
