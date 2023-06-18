package sttp.tapir.serverless.aws.lambda

import sttp.tapir.server.interceptor.CustomiseInterceptors

import scala.concurrent.{ExecutionContext, Future}

object AwsFutureServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customiseInterceptors(implicit ec: ExecutionContext): CustomiseInterceptors[Future, AwsServerOptions[Future]] =
    CustomiseInterceptors(
      createOptions =
        (ci: CustomiseInterceptors[Future, AwsServerOptions[Future]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default(implicit ec: ExecutionContext): AwsServerOptions[Future] = customiseInterceptors.options
}
