package sttp.tapir.serverless.aws.lambda

import sttp.monad.{FutureMonad, MonadError}
import scala.concurrent.{ExecutionContext, Future}
import AwsFutureServerInterpreter._

abstract class AwsFutureServerInterpreter(implicit ec: ExecutionContext) extends AwsServerInterpreter[Future]

object AwsFutureServerInterpreter {

  implicit def futureMonadError(implicit ec: ExecutionContext): MonadError[Future] = new FutureMonad()

  def apply(serverOptions: AwsServerOptions[Future])(implicit ec: ExecutionContext): AwsFutureServerInterpreter = {
    new AwsFutureServerInterpreter {
      override def awsServerOptions: AwsServerOptions[Future] = serverOptions
    }
  }

  def apply()(implicit ec: ExecutionContext): AwsFutureServerInterpreter = {
    new AwsFutureServerInterpreter {
      override def awsServerOptions: AwsServerOptions[Future] = AwsFutureServerOptions.default(ec)
    }
  }
}
