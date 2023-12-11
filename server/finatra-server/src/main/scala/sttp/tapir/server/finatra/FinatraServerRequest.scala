package sttp.tapir.server.finatra

import com.twitter.finagle.http.Request
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.collection.immutable.Seq

case class FinatraServerRequest(request: Request, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override lazy val method: Method = Method(request.method.toString.toUpperCase)
  override lazy val protocol: String = request.version.toString
  override lazy val uri: Uri = Uri.unsafeParse(request.uri)
  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, Some(request.remoteSocketAddress), None)
  override lazy val headers: Seq[Header] = request.headerMap.toList.map { case (k, v) => Header(k, v) }
  override lazy val queryParameters: QueryParams =
    QueryParams.fromMultiMap(request.params.keys.toList.map(k => k -> request.params.getAll(k).toList).toMap)
  override lazy val pathSegments: List[String] = {
    val segments = uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList
    if (segments == List("")) Nil else segments // representing the root path as an empty list
  }
  override def underlying: Any = request
  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): FinatraServerRequest = copy(attributes = attributes.put(k, v))
  override def withUnderlying(underlying: Any): ServerRequest =
    new FinatraServerRequest(request = underlying.asInstanceOf[Request], attributes)
}
