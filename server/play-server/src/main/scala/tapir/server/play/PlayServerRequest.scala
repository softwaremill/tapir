package tapir.server.play

import java.net.{InetSocketAddress, URI}

import play.api.http.HeaderNames
import play.api.mvc.{AnyContent, Request, RequestHeader}
import tapir.model.{ConnectionInfo, Method, ServerRequest}

private[play] class PlayServerRequest(request: Request[AnyContent]) extends ServerRequest {
  private val AbsoluteUri = """(?is)^(https?)://([^/]+):([1-9^/]?)(/.*|$)""".r

  override def method: Method = Method(request.method.toUpperCase)
  override def protocol: String = request.version
  override def uri: URI = new URI(request.uri)
  override def connectionInfo: ConnectionInfo = {
//    val x = req.uri match {
//      case AbsoluteUri(proto, hostPort, rest) => hostPort
//      case _                                  => headers.get(HeaderNames.HOST).getOrElse("")
//    }
//
//    val local = new InetSocketAddress(req.host, req.)
    ConnectionInfo(None, None, None)
  }
  override def headers: Seq[(String, String)] = request.headers.headers
  override def header(name: String): Option[String] = request.headers.get(name)
}
