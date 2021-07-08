package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{ConcurrentEffect, ContextShift, Resource, Sync}
import cats.syntax.all._
import com.typesafe.scalalogging.StrictLogging
import sttp.client3.Identity
import sttp.client3.httpclient.HttpClientSyncBackend
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import scala.concurrent.ExecutionContext

abstract class AwsLambdaRuntime extends StrictLogging {
  def endpoints: Iterable[ServerEndpoint[_, _, _, Any, Identity]]
  implicit def executionContext: ExecutionContext = ExecutionContext.global
  implicit def sync: Sync[Identity] = Sync[Identity]
  implicit def contextShift: ContextShift[Identity] = ContextShift[Identity]
  implicit def concurrentEffect: ConcurrentEffect[Identity] = ConcurrentEffect[Identity]
  def serverOptions: AwsServerOptions[Identity] = AwsServerOptions.customInterceptors()

  def main(args: Array[String]): Unit = {
    val backend = Resource.pure(HttpClientSyncBackend())
    val route: Route[Identity] = AwsCatsEffectServerInterpreter(serverOptions).toRoute(endpoints.toList)
    ConcurrentEffect[Identity].toIO(AwsLambdaRuntimeLogic(route, sys.env("AWS_LAMBDA_RUNTIME_API"), backend)).foreverM.unsafeRunSync()
  }
}
