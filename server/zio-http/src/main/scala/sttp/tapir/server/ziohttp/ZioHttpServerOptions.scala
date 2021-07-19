package sttp.tapir.server.ziohttp

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.interceptor.reject.RejectInterceptor
import sttp.tapir.{Defaults, TapirFile}
import zio.stream.Stream
import zio.{RIO, Task}

case class ZioHttpServerOptions[R](
    createFile: ServerRequest => Task[TapirFile],
    deleteFile: TapirFile => RIO[R, Unit],
    interceptors: List[Interceptor[RIO[R, *], Stream[Throwable, Byte]]]
) {
  def prependInterceptor(i: Interceptor[RIO[R, *], Stream[Throwable, Byte]]): ZioHttpServerOptions[R] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[RIO[R, *], Stream[Throwable, Byte]]): ZioHttpServerOptions[R] =
    copy(interceptors = interceptors :+ i)
}

object ZioHttpServerOptions {

  /** Creates default [[ZioHttpServerOptions]] with `additionalInterceptors`, sitting between two configurable
    * interceptor groups. The order of the interceptors corresponds to the ordering of the parameters.
    *
    * The options can be then further customised using copy constructors or the methods to append/prepend
    * interceptors.
    *
    * @param exceptionHandler Whether to respond to exceptions, or propagate them to vertx.
    * @param rejectInterceptor How to respond when decoding fails for all interpreted endpoints.
    * @param additionalInterceptors Additional interceptors, e.g. handling decode failures, or providing alternate
    *                               responses.
    * @param unsupportedMediaTypeInterceptor Whether to return 415 (unsupported media type) if there's no body in the
    *                                        endpoint's outputs, which can satisfy the constraints from the `Accept`
    *                                        header.
    * @param decodeFailureHandler The decode failure handler, from which an interceptor will be created.
    */
  def customInterceptors[R](
      metricsInterceptor: Option[MetricsRequestInterceptor[RIO[R, *], Stream[Throwable, Byte]]] = None,
      rejectInterceptor: Option[RejectInterceptor[RIO[R, *], Stream[Throwable, Byte]]] = Some(
        RejectInterceptor.default[RIO[R, *], Stream[Throwable, Byte]]
      ),
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      additionalInterceptors: List[Interceptor[RIO[R, *], Stream[Throwable, Byte]]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[RIO[R, *], Stream[Throwable, Byte]]] = Some(
        new UnsupportedMediaTypeInterceptor[RIO[R, *], Stream[Throwable, Byte]]()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): ZioHttpServerOptions[R] =
    ZioHttpServerOptions(
      defaultCreateFile,
      defaultDeleteFile,
      metricsInterceptor.toList ++
        rejectInterceptor.toList ++
        exceptionHandler.map(new ExceptionInterceptor[RIO[R, *], Stream[Throwable, Byte]](_)).toList ++
        additionalInterceptors ++
        unsupportedMediaTypeInterceptor.toList ++
        List(new DecodeFailureInterceptor[RIO[R, *], Stream[Throwable, Byte]](decodeFailureHandler))
    )

  def defaultCreateFile: ServerRequest => Task[TapirFile] = _ => Task.effect(Defaults.createTempFile())

  def defaultDeleteFile[R]: TapirFile => Task[Unit] = file => Task.effect(Defaults.deleteFile()(file))

  def default[R]: ZioHttpServerOptions[R] = customInterceptors()
}
