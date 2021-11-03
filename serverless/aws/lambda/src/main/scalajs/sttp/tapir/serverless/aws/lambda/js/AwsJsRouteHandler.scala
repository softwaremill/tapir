package sttp.tapir.serverless.aws.lambda.js

import sttp.tapir.serverless.aws.lambda.Route

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.JSConverters._

object AwsJsRouteHandler {

  private def toJsRoute(route: Route[Future])(implicit ec: ExecutionContext): JsRoute[Future] = {
    awsJsRequest: AwsJsRequest =>
      route.apply(AwsJsRequest.toAwsRequest(awsJsRequest)).map(AwsJsResponse.fromAwsResponse)
  }

  def handler(event: AwsJsRequest, route: Route[Future])(implicit ec: ExecutionContext): scala.scalajs.js.Promise[AwsJsResponse] = {
    val jsRoute = toJsRoute(route)
    jsRoute(event).toJSPromise
  }
}
