package sttp.tapir.server.http4s

import org.http4s.Request
import sttp.model.Uri.{Authority, FragmentSegment, HostSegment, PathSegments, QuerySegment, UserInfo}
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.{AttributeKey, AttributeMap}

import scala.collection.immutable.Seq

private[http4s] case class Http4sServerRequest[F[_]](req: Request[F], attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override def protocol: String = req.httpVersion.toString()
  override lazy val connectionInfo: ConnectionInfo =
    ConnectionInfo(req.server.map(_.toInetSocketAddress), req.remote.map(_.toInetSocketAddress), req.isSecure)
  override def underlying: Any = req

  /** Can differ from `uri.path`, if the endpoint is deployed in a context */
  override lazy val pathSegments: List[String] = {
    // if the routes are mounted within a context (e.g. using a router), we have to match against what comes
    // after the context. This information is stored in the the PathInfoCaret attribute
    val segments = req.pathInfo.renderString.dropWhile(_ == '/').split("/").toList.map(org.http4s.Uri.decode(_))
    if (segments == List("")) Nil else segments // representing the root path as an empty list
  }

  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(req.multiParams)

  override def method: Method = Method(req.method.name.toUpperCase)
  override lazy val showShort: String = s"$method ${req.uri.copy(scheme = None, authority = None, fragment = None).toString}"
  override lazy val uri: Uri =
    Uri.apply(
      req.uri.scheme.map(_.value),
      req.uri.authority.map(a => Authority(a.userInfo.map(u => UserInfo(u.username, u.password)), HostSegment(a.host.value), a.port)),
      PathSegments.absoluteOrEmptyS(req.uri.path.segments.map(_.decoded()) ++ (if (req.uri.path.endsWithSlash) Seq("") else Nil)),
      req.uri.query.pairs.map(kv => kv._2.map(v => QuerySegment.KeyValue(kv._1, v)).getOrElse(QuerySegment.Value(kv._1))),
      req.uri.fragment.map(f => FragmentSegment(f))
    )
  override lazy val headers: Seq[Header] = req.headers.headers.map(h => Header(h.name.toString, h.value))

  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): Http4sServerRequest[F] = copy(attributes = attributes.put(k, v))

  override def withUnderlying(underlying: Any): ServerRequest =
    Http4sServerRequest(req = underlying.asInstanceOf[Request[F]], attributes)
}
