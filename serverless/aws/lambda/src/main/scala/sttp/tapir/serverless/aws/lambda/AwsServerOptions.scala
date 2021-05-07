package sttp.tapir.serverless.aws.lambda

import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

case class AwsServerOptions[F[_]](interceptors: List[Interceptor[F, String]]) {
  def prependInterceptor(i: Interceptor[F, String]): AwsServerOptions[F] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F, String]): AwsServerOptions[F] = copy(interceptors = interceptors :+ i)
}

object AwsServerOptions {
  def customInterceptors[F[_]](
      metricsInterceptor: Option[MetricsRequestInterceptor[F, String]] = None,
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      // todo log serverLog: Option[ServerLog[Context => F[Unit]]] = None,
      additionalInterceptors: List[Interceptor[F, String]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[F, String]] = Some(
        new UnsupportedMediaTypeInterceptor[F, String]()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): AwsServerOptions[F] = AwsServerOptions(
    metricsInterceptor.toList ++
      exceptionHandler.map(new ExceptionInterceptor[F, String](_)).toList ++
      // todo log
      additionalInterceptors ++
      unsupportedMediaTypeInterceptor.toList ++
      List(new DecodeFailureInterceptor[F, String](decodeFailureHandler))
  )
}
