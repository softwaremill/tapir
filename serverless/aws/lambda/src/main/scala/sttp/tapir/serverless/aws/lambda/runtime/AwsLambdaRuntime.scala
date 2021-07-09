package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{Blocker, ConcurrentEffect, ContextShift}
import cats.syntax.all._
import com.typesafe.scalalogging.StrictLogging
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import scala.concurrent.ExecutionContext

abstract class AwsLambdaRuntime[F[_]: ContextShift: ConcurrentEffect] extends StrictLogging {
  def endpoints: Iterable[ServerEndpoint[_, _, _, Any, F]]
  implicit def executionContext: ExecutionContext = ExecutionContext.global
  def serverOptions: AwsServerOptions[F] = AwsServerOptions.customInterceptors()

  def main(args: Array[String]): Unit = {
    val backend = HttpClientFs2Backend.resource(Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global))
    val route: Route[F] = AwsCatsEffectServerInterpreter(serverOptions).toRoute(endpoints.toList)
    ConcurrentEffect[F].toIO(AwsLambdaRuntimeLogic(route, sys.env("AWS_LAMBDA_RUNTIME_API"), backend)).foreverM.unsafeRunSync()
  }
}
