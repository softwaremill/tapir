package sttp.tapir.server.akkahttp

import java.util.Locale

import akka.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import akka.http.scaladsl.model.{HttpHeader, Uri}
import akka.http.scaladsl.server.RequestContext
import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext

private[akkahttp] class AkkaDecodeInputsContext(req: RequestContext) extends DecodeInputsContext {

  // Add low-level headers that have been removed by akka-http.
  // https://doc.akka.io/docs/akka-http/current/common/http-model.html?language=scala#http-headers
  // https://github.com/softwaremill/tapir/issues/331
  private lazy val allHeaders: List[HttpHeader] = {
    val contentLength = req.request.entity.contentLengthOption.map(`Content-Length`(_))
    val contentType = `Content-Type`(req.request.entity.contentType)
    contentType :: contentLength.toList ++ req.request.headers
  }

  override def method: Method = Method(req.request.method.value)
  override def nextPathSegment: (Option[String], DecodeInputsContext) = {
    req.unmatchedPath match {
      case Uri.Path.Slash(pathTail)      => new AkkaDecodeInputsContext(req.withUnmatchedPath(pathTail)).nextPathSegment
      case Uri.Path.Segment(s, pathTail) => (Some(s), new AkkaDecodeInputsContext(req.withUnmatchedPath(pathTail)))
      case _                             => (None, this)
    }
  }
  override def header(name: String): List[String] = {
    val nameInLowerCase = name.toLowerCase(Locale.ROOT)
    allHeaders.filter(_.is(nameInLowerCase)).map(_.value)
  }
  override def headers: Seq[(String, String)] = allHeaders.map(h => (h.name, h.value))
  override def queryParameter(name: String): Seq[String] = req.request.uri.query().getAll(name).reverse
  override def queryParameters: MultiQueryParams = MultiQueryParams.fromSeq(req.request.uri.query())
  override def bodyStream: Any = req.request.entity.dataBytes
  override def serverRequest: ServerRequest = new AkkaServerRequest(req)
}
