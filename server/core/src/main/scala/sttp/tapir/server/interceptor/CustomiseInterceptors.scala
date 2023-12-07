package sttp.tapir.server.interceptor

import sttp.tapir.server.interceptor.content.NotAcceptableInterceptor
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.interceptor.reject.{DefaultRejectHandler, RejectHandler, RejectInterceptor}
import sttp.tapir.server.model.ValuedEndpointOutput
import sttp.tapir.statusCode

/** Allows customising the interceptors used by the server interpreter. Custom interceptors should usually be added using `addInterceptor`.
  * That way, the custom interceptor is called after the built-in ones (such as logging, metrics, exceptions), and before the decode failure
  * handler. For even more flexibility, interceptors can be added to the beginning or end of the interceptor stack, using
  * `prependInterceptor` and `appendInterceptor`.
  *
  * The first interceptor in the interceptor stack is the one which is called first on request, and processes the resulting response as the
  * last one.
  *
  * Built-in interceptors can be customised or disabled using the dedicated methods.
  *
  * Once done, use [[options]] to obtain the server interpreter options objects, which can be passed to the server interpreter.
  *
  * @param prependedInterceptors
  *   Additional interceptors, which will be called first on request / last on response, e.g. performing logging, metrics, or providing
  *   alternate responses.
  * @param metricsInterceptor
  *   Whether to collect metrics.
  * @param rejectHandler
  *   How to respond when decoding fails for all interpreted endpoints.
  * @param exceptionHandler
  *   Whether to respond to exceptions in the server logic, or propagate them to the server.
  * @param serverLog
  *   The server log using which an interceptor will be created, if any.
  * @param notAcceptableInterceptor
  *   Whether to return 406 (not acceptable) if there's no body in the endpoint's outputs, which can satisfy the constraints from the
  *   `Accept` header.
  * @param additionalInterceptors
  *   Additional interceptors, which will be called before (on request) / after (on response) the `decodeFailureHandler` one, e.g.
  *   performing logging, metrics, or providing alternate responses.
  * @param decodeFailureHandler
  *   The decode failure handler, from which an interceptor will be created. Determines whether to respond when an input fails to decode.
  * @param appendedInterceptors
  *   Additional interceptors, which will be called last on request / first on response, e.g. handling decode failures, or providing
  *   alternate responses.
  */
case class CustomiseInterceptors[F[_], O](
    createOptions: CustomiseInterceptors[F, O] => O,
    prependedInterceptors: List[Interceptor[F]] = Nil,
    metricsInterceptor: Option[MetricsRequestInterceptor[F]] = None,
    corsInterceptor: Option[CORSInterceptor[F]] = None,
    rejectHandler: Option[RejectHandler[F]] = Some(DefaultRejectHandler[F]),
    exceptionHandler: Option[ExceptionHandler[F]] = Some(DefaultExceptionHandler[F]),
    serverLog: Option[ServerLog[F]] = None,
    notAcceptableInterceptor: Option[NotAcceptableInterceptor[F]] = Some(new NotAcceptableInterceptor[F]()),
    additionalInterceptors: List[Interceptor[F]] = Nil,
    decodeFailureHandler: DecodeFailureHandler[F] = DefaultDecodeFailureHandler[F],
    appendedInterceptors: List[Interceptor[F]] = Nil
) {
  def prependInterceptor(i: Interceptor[F]): CustomiseInterceptors[F, O] = copy(prependedInterceptors = prependedInterceptors :+ i)

  def metricsInterceptor(m: MetricsRequestInterceptor[F]): CustomiseInterceptors[F, O] = copy(metricsInterceptor = Some(m))
  def metricsInterceptor(m: Option[MetricsRequestInterceptor[F]]): CustomiseInterceptors[F, O] = copy(metricsInterceptor = m)

  def corsInterceptor(c: CORSInterceptor[F]): CustomiseInterceptors[F, O] = copy(corsInterceptor = Some(c))
  def corsInterceptor(c: Option[CORSInterceptor[F]]): CustomiseInterceptors[F, O] = copy(corsInterceptor = c)

  def rejectHandler(r: RejectHandler[F]): CustomiseInterceptors[F, O] = copy(rejectHandler = Some(r))
  def rejectHandler(r: Option[RejectHandler[F]]): CustomiseInterceptors[F, O] = copy(rejectHandler = r)

  def exceptionHandler(e: ExceptionHandler[F]): CustomiseInterceptors[F, O] = copy(exceptionHandler = Some(e))
  def exceptionHandler(e: Option[ExceptionHandler[F]]): CustomiseInterceptors[F, O] = copy(exceptionHandler = e)

  def serverLog(log: ServerLog[F]): CustomiseInterceptors[F, O] = copy(serverLog = Some(log))
  def serverLog(log: Option[ServerLog[F]]): CustomiseInterceptors[F, O] = copy(serverLog = log)

  def notAcceptableInterceptor(u: NotAcceptableInterceptor[F]): CustomiseInterceptors[F, O] =
    copy(notAcceptableInterceptor = Some(u))
  def notAcceptableInterceptor(u: Option[NotAcceptableInterceptor[F]]): CustomiseInterceptors[F, O] =
    copy(notAcceptableInterceptor = u)

  def addInterceptor(i: Interceptor[F]): CustomiseInterceptors[F, O] = copy(additionalInterceptors = additionalInterceptors :+ i)

  def decodeFailureHandler(d: DecodeFailureHandler[F]): CustomiseInterceptors[F, O] = copy(decodeFailureHandler = d)

  def appendInterceptor(i: Interceptor[F]): CustomiseInterceptors[F, O] = copy(appendedInterceptors = appendedInterceptors :+ i)

  /** Use the default exception, decode failure and reject handlers.
    * @param errorMessageOutput
    *   customise the way error messages are shown in error responses
    * @param notFoundWhenRejected
    *   return a 404 formatted using `errorMessageOutput` when the request was rejected by all endpoints, instead of propagating the
    *   rejection to the server library
    */
  def defaultHandlers(
      errorMessageOutput: String => ValuedEndpointOutput[_],
      notFoundWhenRejected: Boolean = false
  ): CustomiseInterceptors[F, O] = {
    copy(
      exceptionHandler = Some(DefaultExceptionHandler((s, m) => errorMessageOutput(m).prepend(statusCode, s))),
      decodeFailureHandler = DefaultDecodeFailureHandler[F].response(errorMessageOutput),
      rejectHandler = Some(
        DefaultRejectHandler(
          (s, m) => errorMessageOutput(m).prepend(statusCode, s),
          if (notFoundWhenRejected) Some(DefaultRejectHandler.Responses.NotFound) else None
        )
      )
    )
  }

  //

  /** Creates the default interceptor stack */
  def interceptors: List[Interceptor[F]] = prependedInterceptors ++
    metricsInterceptor.toList ++
    corsInterceptor.toList ++
    rejectHandler.map(new RejectInterceptor[F](_)).toList ++
    exceptionHandler.map(new ExceptionInterceptor[F](_)).toList ++
    serverLog.map(new ServerLogInterceptor[F](_)).toList ++
    notAcceptableInterceptor.toList ++
    additionalInterceptors ++
    List(new DecodeFailureInterceptor[F](decodeFailureHandler)) ++
    appendedInterceptors

  def options: O = createOptions(this)
}
