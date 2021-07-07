package sttp.tapir.server.ziohttp

import sttp.tapir.model.ServerRequest
import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.{Defaults, TapirFile}
import zio.blocking.Blocking
import zio.stream.ZStream
import zio.{RIO, Task}

case class ZioHttpServerOptions[R](
    createFile: ServerRequest => Task[TapirFile],
    deleteFile: TapirFile => RIO[R, Unit],
    interceptors: List[Interceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]]]
) {
  def prependInterceptor(i: Interceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]]): ZioHttpServerOptions[R] =
    copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]]): ZioHttpServerOptions[R] =
    copy(interceptors = interceptors :+ i)
}

object ZioHttpServerOptions {

  def customInterceptors[R <: Blocking](
      metricsInterceptor: Option[MetricsRequestInterceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]]] = None,
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      additionalInterceptors: List[Interceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]]] = Some(
        new UnsupportedMediaTypeInterceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]]()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): ZioHttpServerOptions[R] =
    ZioHttpServerOptions(
      defaultCreateFile,
      defaultDeleteFile,
      metricsInterceptor.toList ++
        exceptionHandler.map(new ExceptionInterceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]](_)).toList ++
        additionalInterceptors ++
        unsupportedMediaTypeInterceptor.toList ++
        List(new DecodeFailureInterceptor[RIO[R, *], ZStream[Blocking, Throwable, Byte]](decodeFailureHandler))
    )

  def defaultCreateFile: ServerRequest => Task[TapirFile] = { _ =>
    Task(Defaults.createTempFile())
  }

  def defaultDeleteFile[R]: TapirFile => RIO[R, Unit] = file => {
    RIO(Defaults.deleteFile()(file))
  }

  def default[R <: Blocking]: ZioHttpServerOptions[R] = customInterceptors()
}
