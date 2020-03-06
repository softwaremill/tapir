package sttp.tapir.server.sttpStub

import java.net.URI

import sttp.client.Request
import sttp.model.Method
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

class SttpStubServerRequest[F[_], S](req: Request[F, S]) extends ServerRequest {
  override def method: Method = Method(req.method.method)
  override def protocol: String = "42"
  override def uri: URI = new URI(req.uri.toString())
  override def connectionInfo: ConnectionInfo = ConnectionInfo(None,None,None)
  override def headers: Seq[(String, String)] = req.headers.toList.map(h => (h.name, h.value))
  override def header(name: String): Option[String] = req.headers.find(h=> h.name.toUpperCase() == name.toUpperCase).map(_.value)
}
