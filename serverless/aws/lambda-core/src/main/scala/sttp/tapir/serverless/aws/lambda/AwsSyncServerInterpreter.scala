package sttp.tapir.serverless.aws.lambda

import sttp.monad.MonadError
import sttp.monad.IdentityMonad
import AwsSyncServerInterpreter._

abstract class AwsSyncServerInterpreter extends AwsServerInterpreter[sttp.shared.Identity]

object AwsSyncServerInterpreter {

  implicit val idMonadError: MonadError[sttp.shared.Identity] = IdentityMonad

  def apply(serverOptions: AwsServerOptions[sttp.shared.Identity]): AwsSyncServerInterpreter = {
    new AwsSyncServerInterpreter {
      override def awsServerOptions: AwsServerOptions[sttp.shared.Identity] = serverOptions
    }
  }

  def apply(): AwsSyncServerInterpreter = {
    new AwsSyncServerInterpreter {
      override def awsServerOptions: AwsServerOptions[sttp.shared.Identity] = AwsSyncServerOptions.default
    }
  }
}
