package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.model.{AttributeKeys, Uri as PekkoUri}
import org.apache.pekko.http.scaladsl.server.RequestContext
import sttp.model.Uri.{Authority, FragmentSegment, HostSegment, PathSegments, QuerySegment}
import sttp.model.{Header, HeaderNames, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.{AttributeKey, AttributeMap}

import java.net.{InetAddress, InetSocketAddress}
import scala.annotation.tailrec
import scala.collection.immutable.Seq

private[pekkohttp] case class PekkoServerRequest(ctx: RequestContext, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override def protocol: String = ctx.request.protocol.value
  private lazy val remote = ctx
    .request
    .attribute(AttributeKeys.remoteAddress)
    .flatMap(_.toIP)

  override def connectionInfo: ConnectionInfo = ConnectionInfo(None, remote.map( addr =>
    new InetSocketAddress(addr.ip, addr.port.getOrElse(0))
  ) , None)
  override def underlying: Any = ctx

  override lazy val pathSegments: List[String] = {
    @tailrec
    def run(p: PekkoUri.Path, acc: List[String]): List[String] = p match {
      case PekkoUri.Path.Slash(pathTail)      => run(pathTail, acc)
      case PekkoUri.Path.Segment(s, pathTail) => run(pathTail, s :: acc)
      case _                                  => acc.reverse
    }

    run(ctx.unmatchedPath, Nil)
  }
  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(ctx.request.uri.query().toMultiMap)
  override lazy val method: Method = Method(ctx.request.method.value.toUpperCase)

  private def queryToSegments(query: PekkoUri.Query): List[QuerySegment] = {
    @tailrec
    def run(q: PekkoUri.Query, acc: List[QuerySegment]): List[QuerySegment] = q match {
      case PekkoUri.Query.Cons(k, v, tail) => {
        if (k.isEmpty)
          run(tail, QuerySegment.Value(v) :: acc)
        else if (v.isEmpty)
          run(tail, QuerySegment.Value(k) :: acc)
        else
          run(tail, QuerySegment.KeyValue(k, v) :: acc)
      }
      case PekkoUri.Query.Empty => acc.reverse
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

  // Add low-level headers that have been removed by pekko-http.
  // https://doc.pekko.io/docs/pekko-http/current/common/http-model.html?language=scala#http-headers
  // https://github.com/softwaremill/tapir/issues/331
  override lazy val headers: Seq[Header] = {
    val contentLengthHeader = ctx.request.entity.contentLengthOption.map(cl => Header(HeaderNames.ContentLength, cl.toString))
    val contentType = ctx.request.entity.contentType.value
    val contentTypeHeader =
      if (contentType == EmptyContentType) Nil else List(Header(HeaderNames.ContentType, ctx.request.entity.contentType.value))
    contentTypeHeader ++ contentLengthHeader.toList ++ ctx.request.headers.map(h => Header(h.name(), h.value()))
  }

  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): PekkoServerRequest = copy(attributes = attributes.put(k, v))

  override def withUnderlying(underlying: Any): ServerRequest =
    PekkoServerRequest(ctx = underlying.asInstanceOf[RequestContext], attributes)
}
