package sttp.tapir.serverless.aws.cdk.core

import cats.implicits.toFunctorFilterOps
import sttp.tapir.AnyEndpoint

private[core] case class Request private (method: Method, path: List[Segment])

private[core] object Request {
  def fromEndpoint(endpoint: AnyEndpoint): Option[Request] = {
    for {
      e <- endpoint.method
      method <- Method(e.toString())
    } yield {
      // todo: check if cdk works with leading backslash
      val path = endpoint.showPathTemplate(showQueryParam = None).substring(1)
      Request(method, path.split("/").map(Segment(_)).toList.flattenOption)
    }
  }
}
