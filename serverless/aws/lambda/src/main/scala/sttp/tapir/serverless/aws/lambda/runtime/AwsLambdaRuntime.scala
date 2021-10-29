package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.unsafe.implicits.global
import cats.effect.{Async, IO}
import cats.syntax.all._
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

object AwsLambdaRuntime {
  def apply[F[_]: Async](endpoints: Iterable[ServerEndpoint[_, _, _, _, _, Any, F]], serverOptions: AwsServerOptions[F]): F[Unit] = {
    val backend = HttpClientFs2Backend.resource[F]()
    val route: Route[F] = AwsCatsEffectServerInterpreter(serverOptions).toRoute(endpoints.toList)
    AwsLambdaRuntimeInvocation.handleNext(route, sys.env("AWS_LAMBDA_RUNTIME_API"), backend).foreverM
  }
}

/** A runtime which uses the [[IO]] effect */
abstract class AwsLambdaIORuntime {
  def endpoints: Iterable[ServerEndpoint[_, _, _, _, _, Any, IO]]
  def serverOptions: AwsServerOptions[IO] = AwsServerOptions.default

  def main(args: Array[String]): Unit = AwsLambdaRuntime(endpoints, serverOptions).unsafeRunSync()
}
