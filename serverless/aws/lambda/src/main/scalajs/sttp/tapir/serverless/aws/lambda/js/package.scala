package sttp.tapir.serverless.aws.lambda

import sttp.monad.MonadError

package object js {
  type JsRoute[F[_]] = AwsJsRequest => F[AwsJsResponse]

  implicit class ToJsRouteConverter[F[_]](route: Route[F]) {
    def toJsRoute(implicit monad: MonadError[F]): JsRoute[F] = {
      awsJsRequest: AwsJsRequest =>
        monad.map(route.apply(AwsJsRequest.toAwsRequest(awsJsRequest)))(AwsJsResponse.fromAwsResponse)
    }
  }
}
