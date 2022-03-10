package sttp.tapir.server.interceptor.log

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.{DecodeFailureContext, DecodeSuccessContext, SecurityFailureContext, ServerResponseFromOutput}
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
  def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponseFromOutput[_]): F[Unit]

  /** Invoked when the security logic fails and returns an error. */
  def securityFailureHandled(ctx: SecurityFailureContext[F, _], response: ServerResponseFromOutput[_]): F[Unit]

  /** Invoked when all inputs of the request have been decoded successfully and the endpoint handles the request by providing a response,
    * with the given status code.
    */
  def requestHandled(ctx: DecodeSuccessContext[F, _, _], response: ServerResponseFromOutput[_]): F[Unit]

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
    logLogicExceptions: Boolean = true,
    showEndpoint: AnyEndpoint => String = _.showShort,
    showRequest: ServerRequest => String = _.showShort,
    showResponse: ServerResponseFromOutput[_] => String = _.showShort
) extends ServerLog[F] {

  def doLogWhenHandled(f: (String, Option[Throwable]) => F[Unit]): DefaultServerLog[F] = copy(doLogWhenHandled = f)
  def doLogAllDecodeFailures(f: (String, Option[Throwable]) => F[Unit]): DefaultServerLog[F] = copy(doLogAllDecodeFailures = f)
  def doLogExceptions(f: (String, Throwable) => F[Unit]): DefaultServerLog[F] = copy(doLogExceptions = f)
  def noLog(f: F[Unit]): DefaultServerLog[F] = copy(noLog = f)
  def logWhenHandled(doLog: Boolean): DefaultServerLog[F] = copy(logWhenHandled = doLog)
  def logAllDecodeFailures(doLog: Boolean): DefaultServerLog[F] = copy(logAllDecodeFailures = doLog)
  def logLogicExceptions(doLog: Boolean): DefaultServerLog[F] = copy(logLogicExceptions = doLog)
  def showEndpoint(s: AnyEndpoint => String): DefaultServerLog[F] = copy(showEndpoint = s)
  def showRequest(s: ServerRequest => String): DefaultServerLog[F] = copy(showRequest = s)
  def showResponse(s: ServerResponseFromOutput[_] => String): DefaultServerLog[F] = copy(showResponse = s)

  //

  override def decodeFailureNotHandled(ctx: DecodeFailureContext): F[Unit] =
    if (logAllDecodeFailures)
      doLogAllDecodeFailures(
        s"Request: ${showRequest(ctx.request)}, not handled by: ${showEndpoint(ctx.endpoint)}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}",
        exception(ctx)
      )
    else noLog

  override def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponseFromOutput[_]): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request: ${showRequest(ctx.request)}, handled by: ${showEndpoint(
            ctx.endpoint
          )}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}; response: ${showResponse(response)}",
        exception(ctx)
      )
    else noLog

  override def securityFailureHandled(ctx: SecurityFailureContext[F, _], response: ServerResponseFromOutput[_]): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request: ${showRequest(ctx.request)}, handled by: ${showEndpoint(ctx.endpoint)}; security logic error response: ${showResponse(response)}",
        None
      )
    else noLog

  override def requestHandled(ctx: DecodeSuccessContext[F, _, _], response: ServerResponseFromOutput[_]): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request: ${showRequest(ctx.request)}, handled by: ${showEndpoint(ctx.endpoint)}; response: ${showResponse(response)}",
        None
      )
    else noLog

  override def exception(e: AnyEndpoint, request: ServerRequest, ex: Throwable): F[Unit] =
    if (logLogicExceptions)
      doLogExceptions(s"Exception when handling request: ${showRequest(request)}, by: ${showEndpoint(e)}", ex)
    else noLog

  private def exception(ctx: DecodeFailureContext): Option[Throwable] =
    ctx.failure match {
      case DecodeResult.Error(_, error) => Some(error)
      case _                            => None
    }
}
