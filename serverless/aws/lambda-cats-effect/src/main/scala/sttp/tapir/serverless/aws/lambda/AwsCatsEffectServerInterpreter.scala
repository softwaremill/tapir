package sttp.tapir.serverless.aws.lambda

import cats.effect.Sync
import sttp.client4.impl.cats.implicits._

abstract class AwsCatsEffectServerInterpreter[F[_]](implicit fa: Sync[F]) extends AwsServerInterpreter[F]

object AwsCatsEffectServerInterpreter {

  def apply[F[_]](serverOptions: AwsServerOptions[F])(implicit fa: Sync[F]): AwsCatsEffectServerInterpreter[F] = {
    new AwsCatsEffectServerInterpreter[F] {
      override def awsServerOptions: AwsServerOptions[F] = serverOptions
    }
  }

  def apply[F[_]]()(implicit fa: Sync[F]): AwsCatsEffectServerInterpreter[F] = {
    new AwsCatsEffectServerInterpreter[F] {
      override def awsServerOptions: AwsServerOptions[F] = AwsCatsEffectServerOptions.default[F](fa)
    }
  }
}
