package sttp.tapir.serverless.aws.lambda

import cats.Monad
import sttp.tapir.server.interceptor.CustomiseInterceptors

object AwsCatsEffectServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors[F[_]: Monad]: CustomiseInterceptors[F, AwsServerOptions[F]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[F, AwsServerOptions[F]]) =>
        AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default[F[_]: Monad]: AwsServerOptions[F] = customiseInterceptors.options

  def noEncoding[F[_]: Monad]: AwsServerOptions[F] =
    this.default[F].copy(encodeResponseBody = false)
}
