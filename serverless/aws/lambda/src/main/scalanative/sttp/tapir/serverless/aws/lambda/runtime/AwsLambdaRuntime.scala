package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{Sync, IO, Resource}
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import sttp.client3.{SttpBackend, AbstractCurlBackend, FollowRedirectsBackend}
import sttp.client3.impl.cats.CatsMonadError
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import PlatformCompat.IOPlatformOps

object AwsLambdaRuntime {
  // TODO: Move to sttp
  private class CurlCatsBackend[F[_]: Sync](verbose: Boolean) extends AbstractCurlBackend[F](new CatsMonadError, verbose)
  object CurlCatsBackend {
    def apply[F[_]: Sync](verbose: Boolean = false): SttpBackend[F, Any] =
      new FollowRedirectsBackend(new CurlCatsBackend(verbose))
  }

  def apply[F[_]: Sync](endpoints: Iterable[ServerEndpoint[Any, F]], serverOptions: AwsServerOptions[F]): F[Unit] = {
    val backend = Resource.pure[F, SttpBackend[F, Any]](CurlCatsBackend(verbose = false))
    val route: Route[F] = AwsCatsEffectServerInterpreter(serverOptions).toRoute(endpoints.toList)
    AwsLambdaRuntimeInvocation.handleNext(route, sys.env("AWS_LAMBDA_RUNTIME_API"), backend).foreverM
  }
}

/** A runtime which uses the [[IO]] effect */
abstract class AwsLambdaIORuntime {
  def endpoints: Iterable[ServerEndpoint[Any, IO]]
  def serverOptions: AwsServerOptions[IO] = AwsCatsEffectServerOptions.default[IO]

  def main(args: Array[String]): Unit =
    AwsLambdaRuntime(endpoints, serverOptions).unsafeRunSync()
}
