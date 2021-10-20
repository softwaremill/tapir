package sttp.tapir.server.interceptor.decodefailure

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

class DecodeFailureInterceptor[F[_]](handler: DecodeFailureHandler) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], endpointHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def beforeDecode(request: ServerRequest, serverEndpoint: ServerEndpoint[_, _, _, _, F])(implicit
          monad: MonadError[F]
      ): F[BeforeDecodeResult[F, B]] =
        endpointHandler.beforeDecode(request, serverEndpoint)

      override def onDecodeSuccess[I](
          ctx: DecodeSuccessContext[F, I]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] =
        endpointHandler.onDecodeSuccess(ctx)

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        handler(ctx) match {
          case None               => endpointHandler.onDecodeFailure(ctx)
          case Some(valuedOutput) => responder(ctx.request, valuedOutput).map(Some(_))
        }
      }
    }
}
