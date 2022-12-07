package sttp.tapir.serverless.aws.cdk.internal

import sttp.tapir.AnyEndpoint

private[cdk] case class Request(method: Method, path: List[Segment])

private[cdk] object Request {
  def fromEndpoint(endpoint: AnyEndpoint): Option[Request] = {
    for {
      e <- endpoint.method
      method <- Method(e.toString())
      path = endpoint.showPathTemplate(showQueryParam = None).substring(1)
    } yield Request(method, path.split("/").flatMap(Segment.apply).toList)
  }
}
