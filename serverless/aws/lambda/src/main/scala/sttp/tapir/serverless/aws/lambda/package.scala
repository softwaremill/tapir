package sttp.tapir.serverless.aws

package object lambda {
  type Route[F[_]] = AwsRequest => F[AwsResponse]
}
