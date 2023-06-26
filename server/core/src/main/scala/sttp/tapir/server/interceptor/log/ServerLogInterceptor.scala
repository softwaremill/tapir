package sttp.tapir.server.interceptor.log

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.AnyEndpoint

/** @tparam F The effect in which log messages are returned. */
class ServerLogInterceptor[F[_]](serverLog: ServerLog[F]) extends RequestInterceptor[F] {
  override def apply[R, B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
  ): RequestHandler[F, R, B] = {
    val token = serverLog.requestToken
    val delegate = requestHandler(new ServerLogEndpointInterceptor[F, serverLog.TOKEN](serverLog, token))
    new RequestHandler[F, R, B] {
      override def apply(request: ServerRequest, endpoints: List[ServerEndpoint[R, F]])(implicit
          monad: MonadError[F]
      ): F[RequestResult[B]] = {
        serverLog.requestReceived(request, token).flatMap(_ => delegate(request, endpoints))
      }
    }
  }
}

class ServerLogEndpointInterceptor[F[_], T](serverLog: ServerLog[F] { type TOKEN = T }, token: T) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[A, U, I](ctx: DecodeSuccessContext[F, A, U, I])(implicit
          monad: MonadError[F],
          bodyListener: BodyListener[F, B]
      ): F[ServerResponse[B]] = {
        decodeHandler
          .onDecodeSuccess(ctx)
          .flatMap { response =>
            if (serverLog.ignoreEndpoints.contains(ctx.endpoint))
              response.unit
            else
              serverLog.requestHandled(ctx, response, token).map(_ => response)
          }
          .handleError { case e: Throwable =>
            serverLog
              .exception(ExceptionContext(ctx.endpoint, Some(ctx.securityInput), Some(ctx.principal), ctx.request), e, token)
              .flatMap(_ => monad.error(e))
          }
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        decodeHandler
          .onSecurityFailure(ctx)
          .flatMap { response =>
            serverLog.securityFailureHandled(ctx, response, token).map(_ => response)
          }
          .handleError { case e: Throwable =>
            serverLog
              .exception(ExceptionContext(ctx.endpoint, Some(ctx.securityInput), None, ctx.request), e, token)
              .flatMap(_ => monad.error(e))
          }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        decodeHandler
          .onDecodeFailure(ctx)
          .flatMap {
            case r @ None =>
              serverLog.decodeFailureNotHandled(ctx, token).map(_ => r: Option[ServerResponse[B]])
            case r @ Some(response) =>
              serverLog
                .decodeFailureHandled(ctx, response, token)
                .map(_ => r: Option[ServerResponse[B]])
          }
          .handleError { case e: Throwable =>
            serverLog
              .exception(ExceptionContext(ctx.endpoint, None, None, ctx.request), e, token)
              .flatMap(_ => monad.error(e))
          }
      }
    }
}
