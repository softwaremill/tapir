package sttp.tapir.server.interceptor.log

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.{DecodeFailureContext, DecodeSuccessContext, SecurityFailureContext}
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.{AnyEndpoint, DecodeResult}

import java.time.Clock

/** Used by [[ServerLogInterceptor]] to log how a request was handled.
  * @tparam F[_]
  *   Interpreter-specific effect type constructor.
  */
trait ServerLog[F[_]] {

  /** The type of the per-request token that is generated when a request is started and passed to callbacks when the request is completed.
    * E.g. `Unit` or a timestamp (`Long`).
    */
  type TOKEN

  /** Invoked when the request has been received to obtain the per-request token, such as the starting timestamp. */
  def requestToken: TOKEN

  /** Invoked when the request has been received. */
  def requestReceived(request: ServerRequest, token: TOKEN): F[Unit]

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, haven't provided a
    * response.
    */
  def decodeFailureNotHandled(ctx: DecodeFailureContext, token: TOKEN): F[Unit]

  /** Invoked when there's a decode failure for an input of the endpoint and the interpreter, or other interceptors, provided a response. */
  def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_], token: TOKEN): F[Unit]

  /** Invoked when the security logic fails and returns an error. */
  def securityFailureHandled(ctx: SecurityFailureContext[F, _], response: ServerResponse[_], token: TOKEN): F[Unit]

  /** Invoked when all inputs of the request have been decoded successfully and the endpoint handles the request by providing a response,
    * with the given status code.
    */
  def requestHandled(ctx: DecodeSuccessContext[F, _, _, _], response: ServerResponse[_], token: TOKEN): F[Unit]

  /** Invoked when an exception has been thrown when running the server logic or handling decode failures. */
  def exception(ctx: ExceptionContext[_, _], ex: Throwable, token: TOKEN): F[Unit]

  /** Allows defining a list of endpoints which should not log requestHandled. Exceptions, decode failures and security failures will still
    * be logged.
    */
  def ignoreEndpoints: Set[AnyEndpoint] = Set.empty
}

case class DefaultServerLog[F[_]](
    doLogWhenReceived: String => F[Unit],
    doLogWhenHandled: (String, Option[Throwable]) => F[Unit],
    doLogAllDecodeFailures: (String, Option[Throwable]) => F[Unit],
    doLogExceptions: (String, Throwable) => F[Unit],
    noLog: F[Unit],
    logWhenReceived: Boolean = false,
    logWhenHandled: Boolean = true,
    logAllDecodeFailures: Boolean = false,
    logLogicExceptions: Boolean = true,
    showEndpoint: AnyEndpoint => String = _.showShort,
    showRequest: ServerRequest => String = _.showShort,
    showResponse: ServerResponse[_] => String = _.showShort,
    includeTiming: Boolean = true,
    clock: Clock = Clock.systemUTC(),
    override val ignoreEndpoints: Set[AnyEndpoint] = Set.empty
) extends ServerLog[F] {

  def doLogWhenReceived(f: String => F[Unit]): DefaultServerLog[F] = copy(doLogWhenReceived = f)
  def doLogWhenHandled(f: (String, Option[Throwable]) => F[Unit]): DefaultServerLog[F] = copy(doLogWhenHandled = f)
  def doLogAllDecodeFailures(f: (String, Option[Throwable]) => F[Unit]): DefaultServerLog[F] = copy(doLogAllDecodeFailures = f)
  def doLogExceptions(f: (String, Throwable) => F[Unit]): DefaultServerLog[F] = copy(doLogExceptions = f)
  def noLog(f: F[Unit]): DefaultServerLog[F] = copy(noLog = f)
  def logWhenHandled(doLog: Boolean): DefaultServerLog[F] = copy(logWhenHandled = doLog)
  def logAllDecodeFailures(doLog: Boolean): DefaultServerLog[F] = copy(logAllDecodeFailures = doLog)
  def logLogicExceptions(doLog: Boolean): DefaultServerLog[F] = copy(logLogicExceptions = doLog)
  def showEndpoint(s: AnyEndpoint => String): DefaultServerLog[F] = copy(showEndpoint = s)
  def showRequest(s: ServerRequest => String): DefaultServerLog[F] = copy(showRequest = s)
  def showResponse(s: ServerResponse[_] => String): DefaultServerLog[F] = copy(showResponse = s)
  def includeTiming(doInclude: Boolean): DefaultServerLog[F] = copy(includeTiming = doInclude)
  def clock(c: Clock): DefaultServerLog[F] = copy(clock = c)
  def ignoreEndpoints(es: Seq[AnyEndpoint]): DefaultServerLog[F] = copy(ignoreEndpoints = es.toSet)

  //

  override type TOKEN = Long

  override def requestToken: TOKEN = if (includeTiming) now() else 0

  override def requestReceived(request: ServerRequest, token: TOKEN): F[Unit] =
    if (logWhenReceived) doLogWhenReceived(s"Request received: ${showRequest(request)}") else noLog

  override def decodeFailureNotHandled(ctx: DecodeFailureContext, token: TOKEN): F[Unit] =
    if (logAllDecodeFailures)
      doLogAllDecodeFailures(
        s"Request: ${showRequest(ctx.request)}, not handled by: ${showEndpoint(ctx.endpoint)}${took(token)}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}",
        exception(ctx)
      )
    else noLog

  override def decodeFailureHandled(ctx: DecodeFailureContext, response: ServerResponse[_], token: TOKEN): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request: ${showRequest(ctx.request)}, handled by: ${showEndpoint(
            ctx.endpoint
          )}${took(token)}; decode failure: ${ctx.failure}, on input: ${ctx.failingInput.show}; response: ${showResponse(response)}",
        exception(ctx)
      )
    else noLog

  override def securityFailureHandled(ctx: SecurityFailureContext[F, _], response: ServerResponse[_], token: TOKEN): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request: ${showRequest(ctx.request)}, handled by: ${showEndpoint(ctx.endpoint)}${took(token)}; security logic error response: ${showResponse(response)}",
        None
      )
    else noLog

  override def requestHandled(ctx: DecodeSuccessContext[F, _, _, _], response: ServerResponse[_], token: TOKEN): F[Unit] =
    if (logWhenHandled)
      doLogWhenHandled(
        s"Request: ${showRequest(ctx.request)}, handled by: ${showEndpoint(ctx.endpoint)}${took(token)}; response: ${showResponse(response)}",
        None
      )
    else noLog

  override def exception(ctx: ExceptionContext[_, _], ex: Throwable, token: TOKEN): F[Unit] =
    if (logLogicExceptions)
      doLogExceptions(s"Exception when handling request: ${showRequest(ctx.request)}, by: ${showEndpoint(ctx.endpoint)}${took(token)}", ex)
    else noLog

  private def now() = clock.instant().toEpochMilli

  private def took(token: TOKEN): String = if (includeTiming) s", took: ${now() - token}ms" else ""

  private def exception(ctx: DecodeFailureContext): Option[Throwable] =
    ctx.failure match {
      case DecodeResult.Error(_, error) => Some(error)
      case _                            => None
    }
}
