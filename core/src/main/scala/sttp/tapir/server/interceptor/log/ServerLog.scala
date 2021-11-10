package sttp.tapir.server.interceptor.log

import sttp.tapir.model.{ServerRequest, ServerResponse}
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.{AnyEndpoint, DecodeResult}

/** Used by [[ServerLogInterceptor]] to log how a request was handled.
  * @tparam T
  *   Interpreter-specific value representing the log effect.
  */
trait ServerLog {

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, haven't provided a
    * response.
    */
  def decodeFailureNotHandled(ctx: DecodeFailureContext): Unit

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, provided a response. */
  def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_]): Unit

  /** Invoked when the security logic fails and returns an error. */
  def securityFailureHandled(e: AnyEndpoint, request: ServerRequest, response: ServerResponse[_]): Unit

  /** Invoked when all inputs of the request have been decoded successfully and the endpoint handles the request by providing a response,
    * with the given status code.
    */
  def requestHandled(e: AnyEndpoint, request: ServerRequest, response: ServerResponse[_]): Unit

  /** Invoked when an exception has been thrown when running the server logic or handling decode failures. */
  def exception(e: AnyEndpoint, request: ServerRequest, ex: Throwable): Unit
}

case class DefaultServerLog(
    doLogWhenHandled: (String, Option[Throwable]) => Unit,
    doLogAllDecodeFailures: (String, Option[Throwable]) => Unit,
    doLogExceptions: (String, Throwable) => Unit,
    logWhenHandled: Boolean = true,
    logAllDecodeFailures: Boolean = false,
    logLogicExceptions: Boolean = true
) extends ServerLog {

  override def decodeFailureNotHandled(ctx: DecodeFailureContext): Unit =
    if (logAllDecodeFailures)
      doLogAllDecodeFailures(
        s"Request not handled by: ${ctx.endpoint.show}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}",
        exception(ctx)
      )
    else ()

  override def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_]): Unit =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request handled by: ${ctx.endpoint.show}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}; responding with: $response",
        exception(ctx)
      )
    else ()

  override def securityFailureHandled(e: AnyEndpoint, request: ServerRequest, response: ServerResponse[_]): Unit =
    if (logWhenHandled)
      doLogWhenHandled(s"Request $request handled by: ${e.show}; security logic error response: $response", None)
    else ()

  override def requestHandled(e: AnyEndpoint, request: ServerRequest, response: ServerResponse[_]): Unit =
    if (logWhenHandled)
      doLogWhenHandled(s"Request $request handled by: ${e.show}; responding with code: ${response.code.code}", None)
    else ()

  override def exception(e: AnyEndpoint, request: ServerRequest, ex: Throwable): Unit =
    if (logLogicExceptions)
      doLogExceptions(s"Exception when handling request $request by: ${e.show}", ex)
    else ()

  private def exception(ctx: DecodeFailureContext): Option[Throwable] =
    ctx.failure match {
      case DecodeResult.Error(_, error) => Some(error)
      case _                            => None
    }
}
