package sttp.tapir.serverless.aws

import scala.language.implicitConversions

package object lambda {
  private[lambda] type LambdaResponseBody = (String, Option[Long])

  type Route[F[_]] = AwsRequest => F[AwsResponse]
}
