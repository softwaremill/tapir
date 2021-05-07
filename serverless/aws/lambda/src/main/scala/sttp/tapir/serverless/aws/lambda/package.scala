package sttp.tapir.serverless.aws

import cats.data.Kleisli

package object lambda {
  type Route[F[_]] = Kleisli[F, LambdaRuntimeContext, AwsResponse]
}
