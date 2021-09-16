package sttp.tapir.server.interceptor.decodefailure

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

class DecodeFailureInterceptor[F[_]](handler: DecodeFailureHandler) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        decodeHandler.onDecodeSuccess(ctx)

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        handler(ctx) match {
          case None               => decodeHandler.onDecodeFailure(ctx)
          case Some(valuedOutput) => responder(ctx.request, valuedOutput).map(Some(_))
        }
      }
    }
}
