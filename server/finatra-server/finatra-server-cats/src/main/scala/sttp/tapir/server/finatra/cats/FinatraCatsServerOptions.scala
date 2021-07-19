package sttp.tapir.server.finatra.cats

import cats.effect.std.Dispatcher
import com.twitter.util.Future
import com.twitter.util.logging.Logging
import sttp.tapir.TapirFile
import sttp.tapir.server.finatra.{FinatraContent, FinatraServerOptions}
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

case class FinatraCatsServerOptions[F[_]](
    dispatcher: Dispatcher[F],
    createFile: Array[Byte] => Future[TapirFile],
    deleteFile: TapirFile => Future[Unit],
    interceptors: List[Interceptor[Future, FinatraContent]]
)

object FinatraCatsServerOptions extends Logging {

  /** Creates default [[FinatraCatsServerOptions]] with custom interceptors, sitting between two interceptor groups:
    * 1. the optional exception interceptor and the optional logging interceptor (which should typically be first
    *    when processing the request, and last when processing the response)),
    * 2. the optional unsupported media type interceptor and the decode failure handling interceptor (which should
    *    typically be last when processing the request).
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param serverLog The server log using which an interceptor will be created, if any.
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param unsupportedMediaTypeInterceptor Whether to return 415 (unsupported media type) if there's no body in the
    *                                        endpoint's outputs, which can satisfy the constraints from the `Accept`
    *                                        header.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors[F[_]](
      dispatcher: Dispatcher[F],
      metricsInterceptor: Option[MetricsRequestInterceptor[Future, FinatraContent]] = None,
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLog: Option[ServerLog[Unit]] = Some(FinatraServerOptions.defaultServerLog),
      additionalInterceptors: List[Interceptor[Future, FinatraContent]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[Future, FinatraContent]] = Some(
        new UnsupportedMediaTypeInterceptor()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): FinatraCatsServerOptions[F] =
    FinatraCatsServerOptions(
      dispatcher,
      FinatraServerOptions.defaultCreateFile(FinatraServerOptions.futurePool),
      FinatraServerOptions.defaultDeleteFile(FinatraServerOptions.futurePool),
      metricsInterceptor.toList ++
        exceptionHandler.map(new ExceptionInterceptor[Future, FinatraContent](_)).toList ++
        serverLog.map(sl => new ServerLogInterceptor[Unit, Future, FinatraContent](sl, (_: Unit, _) => Future.Done)).toList ++
        additionalInterceptors ++
        unsupportedMediaTypeInterceptor.toList ++
        List(new DecodeFailureInterceptor[Future, FinatraContent](decodeFailureHandler))
    )

  def default[F[_]](dispatcher: Dispatcher[F]): FinatraCatsServerOptions[F] = customInterceptors(dispatcher)
}
