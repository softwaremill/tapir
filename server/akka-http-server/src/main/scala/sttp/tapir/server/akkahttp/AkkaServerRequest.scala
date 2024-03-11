package sttp.tapir.server.akkahttp

import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.model.headers.{`Content-Length`, `Content-Type`}
import akka.http.scaladsl.model.{Uri => AkkaUri}
import sttp.model.Uri.{Authority, FragmentSegment, HostSegment, PathSegments, QuerySegment}
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.annotation.tailrec
import scala.collection.immutable.Seq

private[akkahttp] case class AkkaServerRequest(ctx: RequestContext, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
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

  private def queryToSegments(query: AkkaUri.Query): List[QuerySegment] = {
    @tailrec
    def run(q: AkkaUri.Query, acc: List[QuerySegment]): List[QuerySegment] = q match {
      case AkkaUri.Query.Cons(k, v, tail) => {
        if (k.isEmpty)
          run(tail, QuerySegment.Value(v) :: acc)
        else if (v.isEmpty)
          run(tail, QuerySegment.Value(k) :: acc)
        else
          run(tail, QuerySegment.KeyValue(k, v) :: acc)
      }
      case AkkaUri.Query.Empty => acc.reverse
    }
    run(query, Nil)
  }

  override lazy val showShort: String = s"$method ${ctx.request.uri.path}${ctx.request.uri.rawQueryString.getOrElse("")}"
  override lazy val uri: Uri = {
    val pekkoUri = ctx.request.uri
    Uri(
      Some(pekkoUri.scheme),
      // UserInfo is available only as a raw string, but we can skip it as it's not needed
      Some(Authority(userInfo = None, HostSegment(pekkoUri.authority.host.address), Some(pekkoUri.effectivePort))),
      PathSegments.absoluteOrEmptyS(pathSegments ++ (if (pekkoUri.path.endsWithSlash) Seq("") else Nil)),
      queryToSegments(ctx.request.uri.query()),
      ctx.request.uri.fragment.map(f => FragmentSegment(f))
    )
  }

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

  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): AkkaServerRequest = copy(attributes = attributes.put(k, v))

  override def withUnderlying(underlying: Any): ServerRequest = AkkaServerRequest(ctx = underlying.asInstanceOf[RequestContext], attributes)
}
