package sttp.tapir.server.akkahttp

import akka.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import akka.http.scaladsl.model.{HttpHeader, Uri}
import akka.http.scaladsl.server.RequestContext
import sttp.model.{Method, QueryParams}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext

import java.util.Locale

private[akkahttp] class AkkaDecodeInputsContext(req: RequestContext) extends DecodeInputsContext {

  private val EmptyContentType = "none/none"

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
    allHeaders.filter(_.is(nameInLowerCase)).map(_.value).filterNot(_ == EmptyContentType)
  }
  override def headers: Seq[(String, String)] = allHeaders.map(h => (h.name, h.value))
  override def queryParameter(name: String): Seq[String] = req.request.uri.query().getAll(name).reverse
  override def queryParameters: QueryParams = QueryParams.fromSeq(req.request.uri.query())
  override def bodyStream: Any = req.request.entity.dataBytes
  override def serverRequest: ServerRequest = new AkkaServerRequest(req)
}
