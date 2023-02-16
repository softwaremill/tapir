package sttp.tapir.serverless.aws.cdk

import cats.effect._
import sttp.tapir.server.ServerEndpoint

trait AwsCdkInterpreter {
  def awsCdkOptions: AwsCdkOptions

  def toCdkAppTemplate[F[_]: Sync](se: ServerEndpoint[Any, F]): CdkAppTemplate[F] = CdkAppTemplate[F](Set(se), awsCdkOptions)

  def toCdkAppTemplate[F[_]: Sync](ses: Iterable[ServerEndpoint[Any, F]]): CdkAppTemplate[F] =
    CdkAppTemplate[F](ses.toSet, awsCdkOptions)
}

object AwsCdkInterpreter {
  def apply(options: AwsCdkOptions): AwsCdkInterpreter =
    new AwsCdkInterpreter {
      override val awsCdkOptions: AwsCdkOptions = options
    }
}
