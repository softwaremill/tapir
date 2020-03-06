package sttp.tapir.server

import sttp.tapir.{DecodeResult, Endpoint}

/**
  * Callbacks to log how a request was handled.
  * @tparam T Interpreter-specific value representing the log action.
  */
case class LogRequestHandling[T](
    doLogWhenHandled: (String, Option[Throwable]) => T,
    doLogAllDecodeFailures: (String, Option[Throwable]) => T,
    doLogLogicExceptions: (String, Throwable) => T,
    noLog: T,
    logWhenHandled: Boolean = true,
    logAllDecodeFailures: Boolean = false,
    logLogicExceptions: Boolean = true
) {

  /**
    * Invoked when there's a decode failure for an input of the endpoint and the [[DecodeFailureHandler]] for the
    * given failure returns a no-match.
    */
  def decodeFailureNotHandled(e: Endpoint[_, _, _, _], ctx: DecodeFailureContext): T = {
    if (logAllDecodeFailures)
      doLogAllDecodeFailures(
        s"Request not handled by: ${e.show}; decode failure: ${ctx.failure}, on input: ${ctx.input.show}",
        exception(ctx)
      )
    else noLog
  }

  /**
    * Invoked when there's a decode failure for an input of the endpoint and the [[DecodeFailureHandler]] for the
    * given failure provides a response.
    */
  def decodeFailureHandled(e: Endpoint[_, _, _, _], ctx: DecodeFailureContext, responseValue: Any): T = {
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request handled by: ${e.show}; decode failure: ${ctx.failure}, on input: ${ctx.input.show}; responding with: $responseValue",
        exception(ctx)
      )
    else noLog
  }

  /**
    * Invoked when all inputs of the request have been decoded successfully and the endpoint handles the request by
    * providing a response (which might be an error or success).
    */
  def requestHandled(e: Endpoint[_, _, _, _], statusCode: Int): T = {
    if (logWhenHandled)
      doLogWhenHandled(s"Request handled by: ${e.show}; responding with code: $statusCode", None)
    else noLog
  }

  /**
    * Invoked when an exception has been thrown when running the server logic.
    */
  def logicException(e: Endpoint[_, _, _, _], ex: Throwable): T = {
    if (logLogicExceptions)
      doLogLogicExceptions(s"Exception when handling request by: ${e.show}", ex)
    else noLog
  }

  private def exception(ctx: DecodeFailureContext): Option[Throwable] = ctx.failure match {
    case DecodeResult.Error(_, error) => Some(error)
    case _                            => None
  }
}
