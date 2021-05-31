package sttp.tapir.serverless.aws.lambda.runtime

import cats.effect.{Blocker, ConcurrentEffect, ContextShift}
import cats.syntax.all._
import com.typesafe.scalalogging.StrictLogging
import org.http4s.client.blaze.BlazeClientBuilder
import sttp.client3.http4s.Http4sBackend
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

abstract class AwsLambdaRuntime[F[_]: ContextShift: ConcurrentEffect] extends StrictLogging {
  def endpoints: Iterable[ServerEndpoint[_, _, _, Any, F]]
  implicit def executionContext: ExecutionContext = ExecutionContext.global
  implicit def serverOptions: AwsServerOptions[F] = AwsServerOptions.customInterceptors()

  def main(args: Array[String]): Unit = {
    val backend = Http4sBackend.usingBlazeClientBuilder(
      BlazeClientBuilder[F](executionContext).withConnectTimeout(0.seconds),
      Blocker.liftExecutionContext(implicitly)
    )
    val route: Route[F] = AwsCatsEffectServerInterpreter.toRoute(endpoints.toList)
    ConcurrentEffect[F].toIO(AwsLambdaRuntimeLogic(route, sys.env("AWS_LAMBDA_RUNTIME_API"), backend)).foreverM.unsafeRunSync()
  }
}
