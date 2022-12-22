package sttp.tapir.serverless.aws.lambda

import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.tapir.server.interpreter.BodyListener

import scala.util.{Success, Try}

private[lambda] class AwsBodyListener[F[_]: MonadError] extends BodyListener[F, LambdaResponseBody] {
  override def onComplete(body: LambdaResponseBody)(cb: Try[Unit] => F[Unit]): F[LambdaResponseBody] = cb(Success(())).map(_ => body)
}
