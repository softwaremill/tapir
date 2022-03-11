package sttp.tapir.server.interceptor.log

import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.{DecodeFailureContext, DecodeSuccessContext, SecurityFailureContext}
import sttp.tapir.{AnyEndpoint, DecodeResult}

/** Used by [[ServerLogInterceptor]] to log how a request was handled.
  * @tparam F[_]
  *   Interpreter-specific effect type constructor.
  */
trait ServerLog[F[_]] {

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, haven't provided a
    * response.
    */
  def decodeFailureNotHandled(ctx: DecodeFailureContext): F[Unit]

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, provided a response. */
  def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_]): F[Unit]

  /** Invoked when the security logic fails and returns an error. */
  def securityFailureHandled(ctx: SecurityFailureContext[F, _], response: ServerResponse[_]): F[Unit]

  /** Invoked when all inputs of the request have been decoded successfully and the endpoint handles the request by providing a response,
    * with the given status code.
    */
  def requestHandled(ctx: DecodeSuccessContext[F, _, _], response: ServerResponse[_]): F[Unit]

  /** Invoked when an exception has been thrown when running the server logic or handling decode failures. */
  def exception(e: AnyEndpoint, request: ServerRequest, ex: Throwable): F[Unit]
}

case class DefaultServerLog[F[_]](
    doLogWhenHandled: (String, Option[Throwable]) => F[Unit],
    doLogAllDecodeFailures: (String, Option[Throwable]) => F[Unit],
    doLogExceptions: (String, Throwable) => F[Unit],
    noLog: F[Unit],
    logWhenHandled: Boolean = true,
    logAllDecodeFailures: Boolean = false,
    logLogicExceptions: Boolean = true
) extends ServerLog[F] {

  override def decodeFailureNotHandled(ctx: DecodeFailureContext): F[Unit] =
    if (logAllDecodeFailures)
      doLogAllDecodeFailures(
        s"Request not handled by: ${ctx.endpoint.show}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}",
        exception(ctx)
      )
    else noLog

  override def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_]): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request handled by: ${ctx.endpoint.show}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}; responding with: $response",
        exception(ctx)
      )
    else noLog

  override def securityFailureHandled(ctx: SecurityFailureContext[F, _], response: ServerResponse[_]): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(s"Request ${ctx.request} handled by: ${ctx.endpoint.show}; security logic error response: $response", None)
    else noLog

  override def requestHandled(ctx: DecodeSuccessContext[F, _, _], response: ServerResponse[_]): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(s"Request ${ctx.request} handled by: ${ctx.endpoint.show}; responding with code: ${response.code.code}", None)
    else noLog

  override def exception(e: AnyEndpoint, request: ServerRequest, ex: Throwable): F[Unit] =
    if (logLogicExceptions)
      doLogExceptions(s"Exception when handling request $request by: ${e.show}", ex)
    else noLog

  private def exception(ctx: DecodeFailureContext): Option[Throwable] =
    ctx.failure match {
      case DecodeResult.Error(_, error) => Some(error)
      case _                            => None
    }
}
