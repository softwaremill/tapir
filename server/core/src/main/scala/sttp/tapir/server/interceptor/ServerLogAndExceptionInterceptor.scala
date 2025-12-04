package sttp.tapir.server.interceptor

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.AnyEndpoint
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor._
import sttp.tapir.server.interceptor.exception.ExceptionHandler
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.model.ServerResponse

import scala.util.control.NonFatal
import sttp.tapir.server.interceptor.log.ServerLog

/** Combines logging and exception handling functionality. When an exception occurs, it is first logged, then optionally transformed into a
  * response, and if a response is generated, it is also logged.
  */
class ServerLogAndExceptionInterceptor[F[_]](serverLog: ServerLog[F], exceptionHandler: ExceptionHandler[F]) extends RequestInterceptor[F] {
  override def apply[R, B](
      responder: Responder[F, B],
      requestHandler: EndpointInterceptor[F] => RequestHandler[F, R, B]
  ): RequestHandler[F, R, B] = {
    val token = serverLog.requestToken
    val delegate = requestHandler(new ServerLogAndExceptionEndpointInterceptor[F, serverLog.TOKEN](serverLog, exceptionHandler, token))
    new RequestHandler[F, R, B] {
      override def apply(request: ServerRequest, endpoints: List[sttp.tapir.server.ServerEndpoint[R, F]])(implicit
          monad: MonadError[F]
      ): F[RequestResult[B]] = {
        serverLog.requestReceived(request, token).flatMap(_ => delegate(request, endpoints)).flatTap {
          // the request was handled by a request handler (in some interceptor), meaning no logging happened as part of the provided
          // ServerLogAndExceptionEndpointInterceptor
          case RequestResult.Response(response, ResponseSource.RequestHandler) =>
            serverLog.requestHandledByInterceptor(request, response, token)
          case _ => ().unit
        }
      }
    }
  }
}

class ServerLogAndExceptionEndpointInterceptor[F[_], T](
    serverLog: ServerLog[F] { type TOKEN = T },
    exceptionHandler: ExceptionHandler[F],
    token: T
) extends EndpointInterceptor[F] {
  override def apply[B](responder: Responder[F, B], decodeHandler: EndpointHandler[F, B]): EndpointHandler[F, B] =
    new EndpointHandler[F, B] {
      override def onDecodeSuccess[A, U, I](ctx: DecodeSuccessContext[F, A, U, I])(implicit
          monad: MonadError[F],
          bodyListener: BodyListener[F, B]
      ): F[ServerResponse[B]] = {
        decodeHandler
          .onDecodeSuccess(ctx)
          .handleError { case NonFatal(e) =>
            onException(e, ctx.endpoint, ctx.request, Some(ctx.securityInput), Some(ctx.principal))
          }
          .flatMap { response =>
            if (serverLog.ignoreEndpoints.contains(ctx.endpoint))
              response.unit
            else
              serverLog.requestHandled(ctx, response, token).map(_ => response)
          }
      }

      override def onSecurityFailure[A](
          ctx: SecurityFailureContext[F, A]
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[ServerResponse[B]] = {
        decodeHandler
          .onSecurityFailure(ctx)
          .handleError { case NonFatal(e) =>
            onException(e, ctx.endpoint, ctx.request, Some(ctx.securityInput), None)
          }
          .flatMap { response =>
            serverLog.securityFailureHandled(ctx, response, token).map(_ => response)
          }
      }

      override def onDecodeFailure(
          ctx: DecodeFailureContext
      )(implicit monad: MonadError[F], bodyListener: BodyListener[F, B]): F[Option[ServerResponse[B]]] = {
        decodeHandler
          .onDecodeFailure(ctx)
          .handleError { case NonFatal(e) =>
            onException(e, ctx.endpoint, ctx.request, None, None).map(Some(_))
          }
          .flatMap {
            case r @ None =>
              serverLog.decodeFailureNotHandled(ctx, token).map(_ => r: Option[ServerResponse[B]])
            case r @ Some(response) =>
              serverLog
                .decodeFailureHandled(ctx, response, token)
                .map(_ => r: Option[ServerResponse[B]])
          }
      }

      private def onException(
          e: Throwable,
          endpoint: AnyEndpoint,
          request: ServerRequest,
          securityInput: Option[Any],
          principal: Option[Any]
      )(implicit
          monad: MonadError[F]
      ): F[ServerResponse[B]] = {
        import sttp.tapir.server.interceptor.log.{ExceptionContext => LogExceptionContext}
        import sttp.tapir.server.interceptor.exception.{ExceptionContext => ExceptionExceptionContext}

        // First, log the exception
        val logExceptionContext =
          LogExceptionContext(endpoint.asInstanceOf[sttp.tapir.Endpoint[Any, ?, ?, ?, ?]], securityInput, principal, request)
        serverLog
          .exception(logExceptionContext, e, token)
          .flatMap { _ =>
            // Then, try to handle the exception
            val handlerContext = ExceptionExceptionContext(e, endpoint, request)
            exceptionHandler(handlerContext).flatMap {
              case Some(output) => responder(request, output) // the response will be logged by the handlers
              case None         => monad.error(e)
            }
          }
      }
    }
}
