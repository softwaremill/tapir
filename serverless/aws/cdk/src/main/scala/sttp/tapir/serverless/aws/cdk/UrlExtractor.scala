package sttp.tapir.serverless.aws.cdk

import sttp.model.Method
import sttp.tapir.AnyEndpoint
import sttp.tapir.EndpointInput.{FixedMethod, FixedPath, PathCapture}
import sttp.tapir.internal.RichEndpoint

case class Url(method: String, path: List[String]) {
  def addPath(segment: String, isParam: Boolean = false): Url =
    if (isParam) this.copy(path = path :+ s"{$segment}") else this.copy(path = path :+ segment)

  def getPath: String = path.mkString("/")

  def withMethod(m: Method): Url = this.copy(method = m.toString())
}

object UrlExtractor {
  def process(endpoint: AnyEndpoint): Url = {
    endpoint.asVectorOfBasicInputs().foldLeft((Url("", List.empty[String]), 0)) { case ((simple, count), input) =>
      input match {
        case FixedMethod(m, _, _) => (simple.withMethod(m), count)
        case FixedPath(s, _, _) => (simple.addPath(s), count)
        case PathCapture(n, _, _) => (simple.addPath(n.getOrElse(s"param$count"), true), count + 1)
        case _ => (simple, count)
      }
    }._1
  }
}
