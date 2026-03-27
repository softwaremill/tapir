package sttp.tapir.serverless.aws.lambda

import sttp.shared.Identity
import sttp.tapir.server.interceptor.CustomiseInterceptors

object AwsSyncServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors: CustomiseInterceptors[Identity, AwsServerOptions[Identity]] =
    CustomiseInterceptors(
      createOptions =
        (ci: CustomiseInterceptors[Identity, AwsServerOptions[Identity]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default: AwsServerOptions[Identity] = customiseInterceptors.options

  def noEncoding: AwsServerOptions[Identity] =
    default.copy(encodeResponseBody = false)
}
