package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.RequestContext
import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext

private[akkahttp] class AkkaDecodeInputsContext(req: RequestContext) extends DecodeInputsContext {
  override def method: Method = Method(req.request.method.value.toUpperCase)
  override def nextPathSegment: (Option[String], DecodeInputsContext) = {
    req.unmatchedPath match {
      case Uri.Path.Slash(pathTail)      => new AkkaDecodeInputsContext(req.withUnmatchedPath(pathTail)).nextPathSegment
      case Uri.Path.Segment(s, pathTail) => (Some(s), new AkkaDecodeInputsContext(req.withUnmatchedPath(pathTail)))
      case _                             => (None, this)
    }
  }
  override def header(name: String): List[String] = req.request.headers.filter(_.is(name.toLowerCase)).map(_.value()).toList
  override def headers: Seq[(String, String)] = req.request.headers.map(h => (h.name(), h.value()))
  override def queryParameter(name: String): Seq[String] = req.request.uri.query().getAll(name).reverse
  override def queryParameters: Map[String, Seq[String]] = MultiQueryParams.fromSeq(req.request.uri.query()).toMultiMap
  override def bodyStream: Any = req.request.entity.dataBytes
  override def serverRequest: ServerRequest = new AkkaServerRequest(req)
}
