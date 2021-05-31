package sttp.tapir.serverless.aws.lambda

import sttp.tapir.server.interceptor.Interceptor
import sttp.tapir.server.interceptor.content.UnsupportedMediaTypeInterceptor
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DecodeFailureInterceptor, DefaultDecodeFailureHandler}
import sttp.tapir.server.interceptor.exception.{DefaultExceptionHandler, ExceptionHandler, ExceptionInterceptor}
import sttp.tapir.server.interceptor.log.ServerLogInterceptor
import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor

case class AwsServerOptions[F[_]](encodeResponseBody: Boolean = true, interceptors: List[Interceptor[F, String]]) {
  def prependInterceptor(i: Interceptor[F, String]): AwsServerOptions[F] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F, String]): AwsServerOptions[F] = copy(interceptors = interceptors :+ i)
}

object AwsServerOptions {
  def customInterceptors[F[_], T](
      encodeResponseBody: Boolean = true,
      metricsInterceptor: Option[MetricsRequestInterceptor[F, String]] = None,
      exceptionHandler: Option[ExceptionHandler] = Some(DefaultExceptionHandler),
      serverLogInterceptor: Option[ServerLogInterceptor[T, F, String]] = None,
      additionalInterceptors: List[Interceptor[F, String]] = Nil,
      unsupportedMediaTypeInterceptor: Option[UnsupportedMediaTypeInterceptor[F, String]] = Some(
        new UnsupportedMediaTypeInterceptor[F, String]()
      ),
      decodeFailureHandler: DecodeFailureHandler = DefaultDecodeFailureHandler.handler
  ): AwsServerOptions[F] = AwsServerOptions(
    encodeResponseBody,
    interceptors = metricsInterceptor.toList ++
      exceptionHandler.map(new ExceptionInterceptor[F, String](_)).toList ++
      serverLogInterceptor.toList ++
      additionalInterceptors ++
      unsupportedMediaTypeInterceptor.toList ++
      List(new DecodeFailureInterceptor[F, String](decodeFailureHandler))
  )
}
