package sttp.tapir.serverless.aws.ziolambda

import sttp.tapir.server.interceptor.CustomiseInterceptors
import sttp.tapir.serverless.aws.lambda.AwsServerOptions
import zio.RIO

object AwsZioServerOptions {
  def customiseInterceptors[R]: CustomiseInterceptors[RIO[R, *], AwsServerOptions[RIO[R, *]]] =
    CustomiseInterceptors(
      createOptions =
        (ci: CustomiseInterceptors[RIO[R, *], AwsServerOptions[RIO[R, *]]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default[R]: AwsServerOptions[RIO[R, *]] = customiseInterceptors.options

  def noEncoding[R]: AwsServerOptions[RIO[R, *]] =
    this.default[R].copy(encodeResponseBody = false)

  def noEncoding[R](options: AwsServerOptions[RIO[R, *]]): AwsServerOptions[RIO[R, *]] =
    options.copy(encodeResponseBody = false)

}
