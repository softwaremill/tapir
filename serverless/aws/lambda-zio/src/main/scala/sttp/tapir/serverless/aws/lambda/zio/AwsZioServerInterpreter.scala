package sttp.tapir.serverless.aws.lambda.zio

import sttp.tapir.serverless.aws.lambda.{AwsServerInterpreter, AwsServerOptions}
import sttp.tapir.ztapir.RIOMonadError
import zio.RIO

private[lambda] abstract class AwsZioServerInterpreter[R: RIOMonadError] extends AwsServerInterpreter[RIO[R, *]]

object AwsZioServerInterpreter {

  def apply[R: RIOMonadError](serverOptions: AwsServerOptions[RIO[R, *]]): AwsZioServerInterpreter[R] =
    new AwsZioServerInterpreter[R] {
      override def awsServerOptions: AwsServerOptions[RIO[R, *]] = serverOptions

    }

  def apply[R: RIOMonadError](): AwsZioServerInterpreter[R] =
    new AwsZioServerInterpreter[R] {
      override def awsServerOptions: AwsServerOptions[RIO[R, *]] = AwsZioServerOptions.default[R]

    }

}
