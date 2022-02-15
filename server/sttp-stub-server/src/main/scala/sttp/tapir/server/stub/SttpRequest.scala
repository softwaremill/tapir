package sttp.tapir.server.stub

import sttp.client3.Request
import sttp.model._
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.collection.immutable.Seq

class SttpRequest(r: Request[_, _]) extends ServerRequest {
  override def method: Method = r.method
  override def headers: Seq[Header] = r.headers
  override def queryParameters: QueryParams = r.uri.params
  override def protocol: String = "HTTP/1.1"
  override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
  override def underlying: Any = r
  override def pathSegments: List[String] = r.uri.pathSegments.segments.map(_.v).toList
  override def uri: Uri = r.uri
}
