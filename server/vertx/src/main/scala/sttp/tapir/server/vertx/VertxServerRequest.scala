package sttp.tapir.server.vertx

import java.net.{InetSocketAddress, URI}

import io.vertx.scala.core.net.SocketAddress
import io.vertx.scala.ext.web.RoutingContext
import sttp.model.Method
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

private[vertx] class VertxServerRequest(rc: RoutingContext) extends ServerRequest {
  private lazy val req = rc.request()
  private lazy val _headers = req.headers()
  lazy val connectionInfo: ConnectionInfo = {
    val conn = req.connection()
    ConnectionInfo(
      Option(conn.localAddress()).map(asInetSocketAddress),
      Option(conn.remoteAddress()).map(asInetSocketAddress),
      Option(conn.isSsl())
    )
  }
  override def method: Method = Method.notValidated(req.rawMethod())
  override def protocol: String = req.scheme().get
  override def uri: URI = new URI(req.uri())
  override def headers: Seq[(String, String)] = _headers.names().map { key =>
      (key, _headers.get(key).get)
    }.toSeq
  override def header(name: String): Option[String] = _headers.get(name)

  private def asInetSocketAddress(address: SocketAddress): InetSocketAddress =
    InetSocketAddress.createUnresolved(address.host(), address.port())
}
