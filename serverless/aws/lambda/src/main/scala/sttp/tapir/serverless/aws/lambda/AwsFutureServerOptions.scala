package sttp.tapir.serverless.aws.lambda

import cats.Monad
import sttp.tapir.server.interceptor.CustomInterceptors
import sttp.tapir.server.interceptor.log.{ServerLog, ServerLogInterceptor}

import scala.concurrent.{ExecutionContext, Future}

object AwsFutureServerOptions {

  /** Allows customising the interceptors used by the server interpreter. */
  def customInterceptors(implicit ec: ExecutionContext): CustomInterceptors[Future, AwsServerOptions[Future]] =
    CustomInterceptors(
      createLogInterceptor = (sl: ServerLog) => new ServerLogInterceptor[Future](sl),
      createOptions =
        (ci: CustomInterceptors[Future, AwsServerOptions[Future]]) => AwsServerOptions(encodeResponseBody = true, ci.interceptors)
    )

  def default(implicit ec: ExecutionContext): AwsServerOptions[Future] = customInterceptors.options
}
