package sttp.tapir.server.interceptor.log

import sttp.tapir.model.ServerResponse
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.{AnyEndpoint, DecodeResult}

/** Used by [[ServerLogInterceptor]] to log how a request was handled.
  * @tparam T
  *   Interpreter-specific value representing the log effect.
  */
trait ServerLog[T] {

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, haven't provided a
    * response.
    */
  def decodeFailureNotHandled(ctx: DecodeFailureContext): T

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, provided a response. */
  def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_]): T

  /** Invoked when the security logic fails and returns an error. */
  def securityFailureHandled(e: AnyEndpoint, response: ServerResponse[_]): T

  /** Invoked when all inputs of the request have been decoded successfully and the endpoint handles the request by providing a response,
    * with the given status code.
    */
  def requestHandled(e: AnyEndpoint, statusCode: Int): T

  /** Invoked when an exception has been thrown when running the server logic or handling decode failures. */
  def exception(e: AnyEndpoint, ex: Throwable): T
}

case class DefaultServerLog[T](
    doLogWhenHandled: (String, Option[Throwable]) => T,
    doLogAllDecodeFailures: (String, Option[Throwable]) => T,
    doLogExceptions: (String, Throwable) => T,
    noLog: T,
    logWhenHandled: Boolean = true,
    logAllDecodeFailures: Boolean = false,
    logLogicExceptions: Boolean = true
) extends ServerLog[T] {

  def decodeFailureNotHandled(ctx: DecodeFailureContext): T =
    if (logAllDecodeFailures)
      doLogAllDecodeFailures(
        s"Request not handled by: ${ctx.endpoint.show}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}",
        exception(ctx)
      )
    else noLog

  def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_]): T =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request handled by: ${ctx.endpoint.show}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}; responding with: $response",
        exception(ctx)
      )
    else noLog

  override def securityFailureHandled(e: AnyEndpoint, response: ServerResponse[_]): T =
    if (logWhenHandled)
      doLogWhenHandled(s"Request handled by: ${e.show}; security logic error response: $response", None)
    else noLog

  def requestHandled(e: AnyEndpoint, statusCode: Int): T =
    if (logWhenHandled)
      doLogWhenHandled(s"Request handled by: ${e.show}; responding with code: $statusCode", None)
    else noLog

  def exception(e: AnyEndpoint, ex: Throwable): T =
    if (logLogicExceptions)
      doLogExceptions(s"Exception when handling request by: ${e.show}", ex)
    else noLog

  private def exception(ctx: DecodeFailureContext): Option[Throwable] =
    ctx.failure match {
      case DecodeResult.Error(_, error) => Some(error)
      case _                            => None
    }
}
