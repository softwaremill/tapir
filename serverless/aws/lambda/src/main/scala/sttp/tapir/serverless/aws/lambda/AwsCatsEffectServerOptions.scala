package sttp.tapir.serverless.aws.lambda

import cats.Monad
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.CustomInterceptors

object AwsCatsEffectServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[F[_]: Monad]: CustomInterceptors[F, AwsServerOptions[F]] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog) => new ServerLogInterceptor[F](sl),
      createOptions = (ci: CustomInterceptors[F, AwsServerOptions[F]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default[F[_]: Monad]: AwsServerOptions[F] = customInterceptors.options
}
