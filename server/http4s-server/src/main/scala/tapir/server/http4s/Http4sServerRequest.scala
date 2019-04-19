package tapir.server.http4s
import java.net.URI

import org.http4s.Request
import org.http4s.util.CaseInsensitiveString
import tapir.model.{ConnectionInfo, Method, ServerRequest}

class Http4sServerRequest[F[_]](req: Request[F]) extends ServerRequest {
  override def method: Method = Method(req.method.name.toUpperCase)
  override def protocol: String = req.httpVersion.toString()
  override def uri: URI = new URI(req.uri.toString())
  override def connectionInfo: ConnectionInfo = ConnectionInfo(req.server, req.remote, req.isSecure)
  override def headers: Seq[(String, String)] = req.headers.toList.map(h => (h.name.value, h.value))
  override def header(name: String): Option[String] = req.headers.get(CaseInsensitiveString(name)).map(_.value)
}
