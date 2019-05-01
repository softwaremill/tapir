package tapir.server

import tapir.{DecodeFailure, DecodeResult, Endpoint, EndpointInput}

/**
  * @param debugLogWhenHandled Should information which endpoint handles a request, by providing a response, be
  *                            DEBUG-logged
  * @param debugLogAllDecodeFailures Should all decode failures be DEBUG-logged, for each endpoint, even if the request
  *                                  is not handled by that endpoint (the {{DecodeFailureHandler}} for the given decode
  *                                  failure returns a no-match result), and the next endpoint will be tried.
  * @param errorLogLogicExceptions Should exceptions that occur when evaluating endpoint server logic be ERROR-logged.
  */
case class LoggingOptions(debugLogWhenHandled: Boolean, debugLogAllDecodeFailures: Boolean, errorLogLogicExceptions: Boolean) {
  def decodeFailureNotHandledMsg(e: Endpoint[_, _, _, _], df: DecodeFailure, input: EndpointInput[_]): Option[String] =
    if (debugLogAllDecodeFailures)
      Some(s"Request not handled by: ${e.show}; decode failure: $df, on input: ${input.show}")
    else None

  def decodeFailureHandledMsg(
      e: Endpoint[_, _, _, _],
      df: DecodeFailure,
      input: EndpointInput[_],
      responseValue: Any
  ): Option[(String, Option[Throwable])] = {
    val failureThrowable = df match {
      case DecodeResult.Error(_, error) => Some(error)
      case _                            => None
    }
    if (debugLogWhenHandled)
      Some(
        (
          s"Request handled by: ${e.show}; decode failure: $df, on input: ${input.show}; responding with: $responseValue",
          failureThrowable
        )
      )
    else None
  }

  def logicExceptionMsg(e: Endpoint[_, _, _, _]): Option[String] =
    if (errorLogLogicExceptions) Some(s"Exception when handling request by: ${e.show}") else None

  def requestHandledMsg(e: Endpoint[_, _, _, _], statusCode: Int): Option[String] =
    if (debugLogWhenHandled) {
      Some(s"Request handled by: ${e.show}; responding with code: $statusCode")
    } else None
}

object LoggingOptions {
  val default: LoggingOptions =
    LoggingOptions(debugLogWhenHandled = true, debugLogAllDecodeFailures = false, errorLogLogicExceptions = true)
}
