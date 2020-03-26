package sttp.tapir.server.stub

import java.net.URI

import sttp.client.Request
import sttp.model.Method
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

class SttpStubServerRequest[_, S](req: Request[_, S]) extends ServerRequest {
  override def method: Method = Method(req.method.method)
  override def protocol: String = "HTTP/1.1"
  override def uri: URI = new URI(req.uri.toString())
  override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
  override def headers: Seq[(String, String)] = req.headers.toList.map(h => (h.name, h.value))
  override def header(name: String): Option[String] = req.headers.find(h => h.name.toUpperCase() == name.toUpperCase).map(_.value)
}
