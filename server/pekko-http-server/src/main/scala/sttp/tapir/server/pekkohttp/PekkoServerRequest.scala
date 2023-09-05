package sttp.tapir.server.pekkohttp

import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.http.scaladsl.model.{Uri => PekkoUri}
import sttp.model.{Header, HeaderNames, Method, QueryParams, Uri}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.annotation.tailrec
import scala.collection.immutable.Seq

private[pekkohttp] case class PekkoServerRequest(ctx: RequestContext, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override def protocol: String = ctx.request.protocol.value
  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
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
  override lazy val uri: Uri = Uri.unsafeParse(ctx.request.uri.toString())

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
