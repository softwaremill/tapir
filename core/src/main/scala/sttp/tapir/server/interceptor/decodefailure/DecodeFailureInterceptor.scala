package sttp.tapir.server.interceptor.decodefailure

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor._

class DecodeFailureInterceptor[F[_], B](handler: DecodeFailureHandler) extends EndpointInterceptor[F, B] {
  override def apply(responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] = new EndpointHandler[F, B] {
    override def onDecodeSuccess[I](ctx: DecodeSuccessContext[F, I])(implicit monad: MonadError[F]): F[ServerResponse[B]] =
      decodeHandler.onDecodeSuccess(ctx)

    override def onDecodeFailure(ctx: DecodeFailureContext)(implicit monad: MonadError[F]): F[Option[ServerResponse[B]]] = {
      handler(ctx) match {
        case None               => decodeHandler.onDecodeFailure(ctx)
        case Some(valuedOutput) => responder(valuedOutput).map(Some(_))
      }
    }
  }
}
