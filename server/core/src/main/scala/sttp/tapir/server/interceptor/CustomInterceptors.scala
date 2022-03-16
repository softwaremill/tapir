package sttp.tapir.server.interceptor

import sttp.tapir.server.ValuedEndpointOutput
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.interceptor.reject.{DefaultRejectHandler, RejectHandler, RejectInterceptor}
import sttp.tapir.{headers, statusCode}

/** Allows customising the interceptors used by the server interpreter. Custom interceptors can be added via `addInterceptor`, sitting
  * between two configurable, default interceptor groups.
  *
  * The order of the interceptors corresponds to the ordering of the parameters.
  *
  * Moreover, built-in interceptors can be customised or disabled. Once done, use `.options` to obtain the server interpreter options to
  * use.
  *
  * @param metricsInterceptor
  *   Whether to collect metrics.
  * @param rejectHandler
  *   How to respond when decoding fails for all interpreted endpoints.
  * @param exceptionHandler
  *   Whether to respond to exceptions in the server logic, or propagate them to the server.
  * @param serverLog
  *   The server log using which an interceptor will be created, if any.
  * @param additionalInterceptors
  *   Additional interceptors, e.g. handling decode failures, or providing alternate responses.
  * @param unsupportedMediaTypeInterceptor
  *   Whether to return 415 (unsupported media type) if there's no body in the endpoint's outputs, which can satisfy the constraints from
  *   the `Accept` header.
  * @param decodeFailureHandler
  *   The decode failure handler, from which an interceptor will be created. Determines whether to respond when an input fails to decode.
  */
case class CustomInterceptors[F[_], O](
    createOptions: CustomInterceptors[F, O] => O,
    metricsInterceptor: Option[MetricsRequestInterceptor[F]] = None,
    corsInterceptor: Option[CORSInterceptor[F]] = None,
    rejectHandler: Option[RejectHandler] = Some(DefaultRejectHandler.default),
    exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler.handler),
    serverLog: Option[ServerLog[F]] = None,
    additionalInterceptors: List[Interceptor[F]] = Nil,
    unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[F]] = Some(
      new UnsupportedMediaTypeInterceptor[F]()
    ),
    decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.default
) {
  def metricsInterceptor(m: MetricsRequestInterceptor[F]): CustomInterceptors[F, O] = copy(metricsInterceptor = Some(m))
  def metricsInterceptor(m: Option[MetricsRequestInterceptor[F]]): CustomInterceptors[F, O] = copy(metricsInterceptor = m)

  def corsInterceptor(c: CORSInterceptor[F]): CustomInterceptors[F, O] = copy(corsInterceptor = Some(c))
  def corsInterceptor(c: Option[CORSInterceptor[F]]): CustomInterceptors[F, O] = copy(corsInterceptor = c)

  def rejectHandler(r: RejectHandler): CustomInterceptors[F, O] = copy(rejectHandler = Some(r))
  def rejectHandler(r: Option[RejectHandler]): CustomInterceptors[F, O] = copy(rejectHandler = r)

  def exceptionHandler(e: ExceptionHandler): CustomInterceptors[F, O] = copy(exceptionHandler = Some(e))
  def exceptionHandler(e: Option[ExceptionHandler]): CustomInterceptors[F, O] = copy(exceptionHandler = e)

  def serverLog(log: ServerLog[F]): CustomInterceptors[F, O] = copy(serverLog = Some(log))
  def serverLog(log: Option[ServerLog[F]]): CustomInterceptors[F, O] = copy(serverLog = log)

  def addInterceptor(i: Interceptor[F]): CustomInterceptors[F, O] =
    copy(additionalInterceptors = additionalInterceptors :+ i)

  def unsupportedMediaTypeInterceptor(u: UnsupportedMediaTypeInterceptor[F]): CustomInterceptors[F, O] =
    copy(unsupportedMediaTypeInterceptor = Some(u))
  def unsupportedMediaTypeInterceptor(u: Option[UnsupportedMediaTypeInterceptor[F]]): CustomInterceptors[F, O] =
    copy(unsupportedMediaTypeInterceptor = u)

  def decodeFailureHandler(d: DecodeFailureHandler): CustomInterceptors[F, O] = copy(decodeFailureHandler = d)

  /** Customise the way error messages are shown in error responses, using the default exception, decode failure and reject handlers. */
  def errorOutput(errorMessageOutput: String => ValuedEndpointOutput[_]): CustomInterceptors[F, O] = {
    copy(
      exceptionHandler = Some(DefaultExceptionHandler((s, m) => errorMessageOutput(m).prepend(statusCode, s))),
      decodeFailureHandler =
        DefaultDecodeFailureHandler.default.copy(response = (s, h, m) => errorMessageOutput(m).prepend(statusCode.and(headers), (s, h))),
      rejectHandler = Some(DefaultRejectHandler((s, m) => errorMessageOutput(m).prepend(statusCode, s)))
    )
  }

  //

  /** Creates the default interceptor stack */
  def interceptors: List[Interceptor[F]] = metricsInterceptor.toList ++
    corsInterceptor.toList ++
    rejectHandler.map(new RejectInterceptor[F](_)).toList ++
    exceptionHandler.map(new ExceptionInterceptor[F](_)).toList ++
    serverLog.map(new ServerLogInterceptor[F](_)).toList ++
    additionalInterceptors ++
    unsupportedMediaTypeInterceptor.toList ++
    List(new DecodeFailureInterceptor[F](decodeFailureHandler))

  def options: O = createOptions(this)
}
