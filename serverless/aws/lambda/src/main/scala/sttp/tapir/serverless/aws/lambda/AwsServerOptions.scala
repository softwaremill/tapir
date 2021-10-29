package sttp.tapir.serverless.aws.lambda

import sttp.monad.MonadError
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.{CustomInterceptors, Interceptor}

case class AwsServerOptions[F[_]](encodeResponseBody: Boolean = true, interceptors: List[Interceptor[F]]) {
  def prependInterceptor(i: Interceptor[F]): AwsServerOptions[F] = copy(interceptors = i :: interceptors)
  def appendInterceptor(i: Interceptor[F]): AwsServerOptions[F] = copy(interceptors = interceptors :+ i)
}

object AwsServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[F[_]: MonadError](implicit monadError: MonadError[F]): CustomInterceptors[F, Unit, AwsServerOptions[F]] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, F](sl, (_, _) => monadError.unit(())),
      createOptions = (ci: CustomInterceptors[F, Unit, AwsServerOptions[F]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default[F[_]: MonadError]: AwsServerOptions[F] = customInterceptors.options
}
