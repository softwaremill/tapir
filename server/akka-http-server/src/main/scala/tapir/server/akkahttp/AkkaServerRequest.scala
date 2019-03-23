package tapir.server.akkahttp

import java.net.URI

import akka.http.scaladsl.server.RequestContext
import tapir.model.{ConnectionInfo, Method, ServerRequest}

private[akkahttp] class AkkaServerRequest(ctx: RequestContext) extends ServerRequest {
  override def method: Method = Method(ctx.request.method.value.toUpperCase)
  override def protocol: String = ctx.request.protocol.value
  override def uri: URI = new URI(ctx.request.uri.toString())
  override def connectionInfo: ConnectionInfo = ConnectionInfo(None, None, None)
  override def headers: Seq[(String, String)] = ctx.request.headers.map(h => (h.name(), h.value()))
  override def header(name: String): Option[String] = ctx.request.headers.filter(_.is(name.toLowerCase)).map(_.value()).headOption
}
