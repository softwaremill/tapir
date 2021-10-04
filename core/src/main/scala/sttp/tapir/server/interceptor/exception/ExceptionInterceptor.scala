package sttp.tapir.server.interceptor.exception

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.control.NonFatal

class ExceptionInterceptor[F[_]](handler: ExceptionHandler) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        monad.handleError(decodeHandler.onDecodeSuccess(ctx)) { case NonFatal(e) =>
          handler(ExceptionContext(e, ctx.endpoint, ctx.request)) match {
            case Some(value) => responder(ctx.request, value)
            case None        => monad.error(e)
          }
        }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        monad.handleError(decodeHandler.onDecodeFailure(ctx)) { case NonFatal(e) =>
          handler(ExceptionContext(e, ctx.endpoint, ctx.request)) match {
            case Some(value) => responder(ctx.request, value).map(Some(_))
            case None        => monad.error(e)
          }
        }
      }
    }
}
