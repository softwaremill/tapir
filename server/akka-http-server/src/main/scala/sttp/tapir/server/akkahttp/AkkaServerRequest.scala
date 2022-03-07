package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import akka.http.scaladsl.model.{Uri => AkkaUri}
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.annotation.tailrec
import scala.collection.immutable.Seq

private[akkahttp] class AkkaServerRequest(ctx: RequestContext) extends ServerRequest {
  override def protocol: String = ctx.request.protocol.value
  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
  override def underlying: Any = ctx

  override lazy val pathSegments: List[String] = {
    @tailrec
    def run(p: AkkaUri.Path, acc: List[String]): List[String] = p match {
      case AkkaUri.Path.Slash(pathTail)      => run(pathTail, acc)
      case AkkaUri.Path.Segment(s, pathTail) => run(pathTail, s :: acc)
      case _                                 => acc.reverse
    }

    run(ctx.unmatchedPath, Nil)
  }
  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(ctx.request.uri.query().toMultiMap)
  override lazy val method: Method = Method(ctx.request.method.value.toUpperCase)
  override lazy val uri: Uri = Uri.unsafeParse(ctx.request.uri.toString())

  private val EmptyContentType = "none/none"

  // Add low-level headers that have been removed by akka-http.
  // https://doc.akka.io/docs/akka-http/current/common/http-model.html?language=scala#http-headers
  // https://github.com/softwaremill/tapir/issues/331
  override lazy val headers: Seq[Header] = {
    val contentLength = ctx.request.entity.contentLengthOption.map(`Content-Length`(_))
    val contentType = `Content-Type`(ctx.request.entity.contentType)
    val akkaHeaders = contentType :: contentLength.toList ++ ctx.request.headers
    akkaHeaders.filterNot(_.value == EmptyContentType).map(h => Header(h.name(), h.value()))
  }

  override def withUnderlying(underlying: Any): ServerRequest = new AkkaServerRequest(ctx = underlying.asInstanceOf[RequestContext])
}
