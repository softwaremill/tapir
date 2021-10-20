package sttp.tapir.server.finatra

import com.twitter.finagle.http.Request
import io.netty.handler.codec.http.QueryStringDecoder
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{AttributeKey, AttributeMap, ConnectionInfo, ServerRequest}

import scala.collection.immutable.Seq

class FinatraServerRequest(request: Request, attributeMap: AttributeMap = new AttributeMap()) extends ServerRequest {
  override lazy val method: Method = Method(request.method.toString.toUpperCase)
  override lazy val protocol: String = request.version.toString
  override lazy val uri: Uri = Uri.unsafeParse(request.uri)
  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, Some(request.remoteSocketAddress), None)
  override lazy val headers: Seq[Header] = request.headerMap.toList.map { case (k, v) => Header(k, v) }
  override lazy val queryParameters: QueryParams =
    QueryParams.fromMultiMap(request.params.keys.toList.map(k => k -> request.params.getAll(k).toList).toMap)
  override lazy val pathSegments: List[String] = request.path.dropWhile(_ == '/').split("/").toList.map(QueryStringDecoder.decodeComponent)
  override def underlying: Any = request
  override def attribute[T](key: AttributeKey[T]): Option[T] = attributeMap.get(key)
  override def withAttribute[T](key: AttributeKey[T], value: T): ServerRequest =
    new FinatraServerRequest(request, attributeMap.put(key, value))
}
