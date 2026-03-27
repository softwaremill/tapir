package sttp.tapir.serverless.aws.lambda

import sttp.tapir.server.interceptor.CustomiseInterceptors

object AwsSyncServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors: CustomiseInterceptors[sttp.shared.Identity, AwsServerOptions[sttp.shared.Identity]] =
    CustomiseInterceptors(
      createOptions = (ci: CustomiseInterceptors[sttp.shared.Identity, AwsServerOptions[sttp.shared.Identity]]) =>
        AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default: AwsServerOptions[sttp.shared.Identity] = customiseInterceptors.options

  def noEncoding: AwsServerOptions[sttp.shared.Identity] =
    default.copy(encodeResponseBody = false)
}
