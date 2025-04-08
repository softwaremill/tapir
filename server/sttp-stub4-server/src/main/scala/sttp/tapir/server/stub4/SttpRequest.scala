package sttp.tapir.server.stub4

import sttp.client4.GenericRequest
import sttp.model._
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.{AttributeKey, AttributeMap}

import scala.collection.immutable.Seq

case class SttpRequest(r: GenericRequest[_, _], attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override def method: Method = r.method
  override def headers: Seq[Header] = r.headers
  override def queryParameters: QueryParams = r.uri.params
  override def protocol: String = "HTTP/1.1"
  override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
  override def underlying: Any = r
  override def pathSegments: List[String] = (r.uri.pathSegments.segments.map(_.v) match {
    case other :+ "" => other
    case s           => s
  }).toList
  override def uri: Uri = r.uri
  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): SttpRequest = copy(attributes = attributes.put(k, v))
  override def withUnderlying(underlying: Any): ServerRequest =
    new SttpRequest(r = underlying.asInstanceOf[GenericRequest[_, _]], attributes)
}
