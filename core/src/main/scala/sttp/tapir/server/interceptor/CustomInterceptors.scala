package sttp.tapir.server.interceptor

import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.ServerLog
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.interceptor.reject.RejectInterceptor
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
  * @param rejectInterceptor
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
    createLogInterceptor: ServerLog => Interceptor[F],
    createOptions: CustomInterceptors[F, O] => O,
    metricsInterceptor: Option[MetricsRequestInterceptor[F]] = None,
    rejectInterceptor: Option[RejectInterceptor[F]] = Some(RejectInterceptor.default[F]),
    exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler.handler),
    serverLog: Option[ServerLog] = None,
    additionalInterceptors: List[Interceptor[F]] = Nil,
    unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[F]] = Some(
      new UnsupportedMediaTypeInterceptor[F]()
    ),
    decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
) {
  def metricsInterceptor(m: MetricsRequestInterceptor[F]): CustomInterceptors[F, O] = copy(metricsInterceptor = Some(m))
  def metricsInterceptor(m: Option[MetricsRequestInterceptor[F]]): CustomInterceptors[F, O] = copy(metricsInterceptor = m)

  def rejectInterceptor(r: RejectInterceptor[F]): CustomInterceptors[F, O] = copy(rejectInterceptor = Some(r))
  def rejectInterceptor(r: Option[RejectInterceptor[F]]): CustomInterceptors[F, O] = copy(rejectInterceptor = r)

  def exceptionHandler(e: ExceptionHandler): CustomInterceptors[F, O] = copy(exceptionHandler = Some(e))
  def exceptionHandler(e: Option[ExceptionHandler]): CustomInterceptors[F, O] = copy(exceptionHandler = e)

  def serverLog(log: ServerLog): CustomInterceptors[F, O] = copy(serverLog = Some(log))
  def serverLog(log: Option[ServerLog]): CustomInterceptors[F, O] = copy(serverLog = log)

  def addInterceptor(i: Interceptor[F]): CustomInterceptors[F, O] =
    copy(additionalInterceptors = additionalInterceptors :+ i)

  def unsupportedMediaTypeInterceptor(u: UnsupportedMediaTypeInterceptor[F]): CustomInterceptors[F, O] =
    copy(unsupportedMediaTypeInterceptor = Some(u))
  def unsupportedMediaTypeInterceptor(u: Option[UnsupportedMediaTypeInterceptor[F]]): CustomInterceptors[F, O] =
    copy(unsupportedMediaTypeInterceptor = u)

  def decodeFailureHandler(d: DecodeFailureHandler): CustomInterceptors[F, O] = copy(decodeFailureHandler = d)

  /** Customise the way error messages are rendered in error responses, using the default exception and decode failure handlers.
    */
  def errorOutput(errorMessageOutput: String => ValuedEndpointOutput[_]): CustomInterceptors[F, O] = {
    copy(
      exceptionHandler = Some(DefaultExceptionHandler((s, m) => errorMessageOutput(m).prepend(statusCode, s))),
      decodeFailureHandler =
        DefaultDecodeFailureHandler.handler.copy(response = (s, h, m) => errorMessageOutput(m).prepend(statusCode.and(headers), (s, h)))
    )
  }

  //

  /** Creates the default interceptor stack */
  def interceptors: List[Interceptor[F]] = metricsInterceptor.toList ++
    rejectInterceptor.toList ++
    exceptionHandler.map(new ExceptionInterceptor[F](_)).toList ++
    serverLog.map(createLogInterceptor).toList ++
    additionalInterceptors ++
    unsupportedMediaTypeInterceptor.toList ++
    List(new DecodeFailureInterceptor[F](decodeFailureHandler))

  def options: O = createOptions(this)
}
