package sttp.tapir.serverless.aws.lambda

package object js {
  type JsRoute[F[_]] = AwsJsRequest => F[AwsJsResponse]
}
