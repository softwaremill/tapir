package sttp.tapir.serverless.aws.lambda

import cats.Monad
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}
import sttp.tapir.server.interceptor.CustomInterceptors

object AwsCatsEffectServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors[F[_]: Monad]: CustomInterceptors[F, Unit, AwsServerOptions[F]] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog[Unit]) => new ServerLogInterceptor[Unit, F](sl, (_, _) => Monad[F].unit),
      createOptions = (ci: CustomInterceptors[F, Unit, AwsServerOptions[F]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default[F[_]: Monad]: AwsServerOptions[F] = customInterceptors.options
}
