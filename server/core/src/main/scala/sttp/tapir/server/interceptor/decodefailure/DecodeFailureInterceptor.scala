package sttp.tapir.server.interceptor.decodefailure

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

class DecodeFailureInterceptor[F[_]](handler: DecodeFailureHandler[F]) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[A, U, I](
          ctx: DecodeSuccessContext[F, A, U, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        endpointHandler.onDecodeSuccess(ctx)

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        endpointHandler.onSecurityFailure(ctx)

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        handler(ctx).flatMap {
          case None               => endpointHandler.onDecodeFailure(ctx)
          case Some(valuedOutput) => responder(ctx.request, valuedOutput).map(Some(_))
        }
      }
    }
}
