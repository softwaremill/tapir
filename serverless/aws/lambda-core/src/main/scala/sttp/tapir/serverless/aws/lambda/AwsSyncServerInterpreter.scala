package sttp.tapir.serverless.aws.lambda

import sttp.monad.MonadError
import sttp.monad.IdentityMonad
import sttp.shared.Identity
import AwsSyncServerInterpreter._

abstract class AwsSyncServerInterpreter extends AwsServerInterpreter[Identity]

object AwsSyncServerInterpreter {

  implicit val idMonadError: MonadError[Identity] = IdentityMonad

  def apply(serverOptions: AwsServerOptions[Identity]): AwsSyncServerInterpreter = {
    new AwsSyncServerInterpreter {
      override def awsServerOptions: AwsServerOptions[Identity] = serverOptions
    }
  }

  def apply(): AwsSyncServerInterpreter = {
    new AwsSyncServerInterpreter {
      override def awsServerOptions: AwsServerOptions[Identity] = AwsSyncServerOptions.default
    }
  }
}
