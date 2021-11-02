package sttp.tapir.serverless.aws.lambda

import scala.concurrent.{ExecutionContext, Future}

package object js {
  type JsRoute[F[_]] = AwsJsRequest => F[AwsJsResponse]

  implicit class ToJsRouteConverter(route: Route[Future]) {
    def toJsRoute(implicit ec: ExecutionContext): JsRoute[Future] = {
      awsJsRequest: AwsJsRequest =>
        route.apply(AwsJsRequest.toAwsRequest(awsJsRequest)).map(AwsJsResponse.fromAwsResponse)
    }
  }
}
