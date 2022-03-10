package sttp.tapir.server.interceptor.log

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener

/** @tparam F The effect in which log messages are returned. */
class ServerLogInterceptor[F[_]](serverLog: ServerLog[F]) extends RequestInterceptor[F] {

  /** @tparam B The interpreter-specific, low-level type of body. */
  override def apply[B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, B]
  ): RequestHandler[F, B] = {
    requestHandler(new ServerLogEndpointInterceptor[F, serverLog.TOKEN](serverLog, serverLog.requestStarted))
  }
}

class ServerLogEndpointInterceptor[F[_], T](serverLog: ServerLog[F] { type TOKEN = T }, token: T) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[U, I](ctx: DecodeSuccessContext[F, U, I])(implicit
          monad: MonadError[F],
          bodyListener: BodyListener[F, B]
      ): F[ServerResponseFromOutput[B]] = {
        decodeHandler
          .onDecodeSuccess(ctx)
          .flatMap { response =>
            serverLog.requestHandled(ctx, response, token).map(_ => response)
          }
          .handleError { case e: Throwable =>
            serverLog.exception(ctx.endpoint, ctx.request, e, token).flatMap(_ => monad.error(e))
          }
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponseFromOutput[B]] = {
        decodeHandler
          .onSecurityFailure(ctx)
          .flatMap { response =>
            serverLog.securityFailureHandled(ctx, response, token).map(_ => response)
          }
          .handleError { case e: Throwable =>
            serverLog.exception(ctx.endpoint, ctx.request, e, token).flatMap(_ => monad.error(e))
          }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponseFromOutput[B]]] = {
        decodeHandler
          .onDecodeFailure(ctx)
          .flatMap {
            case r @ None =>
              serverLog.decodeFailureNotHandled(ctx, token).map(_ => r: Option[ServerResponseFromOutput[B]])
            case r @ Some(response) =>
              serverLog
                .decodeFailureHandled(ctx, response, token)
                .map(_ => r: Option[ServerResponseFromOutput[B]])
          }
          .handleError { case e: Throwable =>
            serverLog.exception(ctx.endpoint, ctx.request, e, token).flatMap(_ => monad.error(e))
          }
      }
    }
}
