package sttp.tapir.serverless.aws.lambda

import sttp.tapir.server.interceptor.Interceptor

case class AwsServerOptions[F[_]](encodeResponseBody: Boolean = true, interceptors: List[Interceptor[F]]) {
  def prependInterceptor(i: Interceptor[F]): AwsServerOptions[F] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): AwsServerOptions[F] = copy(interceptors = interceptors :+ i)
}
