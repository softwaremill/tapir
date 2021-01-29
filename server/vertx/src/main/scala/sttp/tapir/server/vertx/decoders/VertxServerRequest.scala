package sttp.tapir.server.vertx.decoders

import java.net.{InetSocketAddress, URI}

import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext
import sttp.model.Method
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.vertx.routing.MethodMapping

import scala.collection.JavaConverters._

private[vertx] class VertxServerRequest(rc: RoutingContext) extends ServerRequest {
  private lazy val req = rc.request
  private lazy val _headers = req.headers
  lazy val connectionInfo: ConnectionInfo = {
    val conn = req.connection
    ConnectionInfo(
      Option(conn.localAddress).map(asInetSocketAddress),
      Option(conn.remoteAddress).map(asInetSocketAddress),
      Option(conn.isSsl)
    )
  }
  override def method: Method = MethodMapping.vertxToSttp(req)
  override def protocol: String = Option(req.scheme).getOrElse("")
  override def uri: URI = new URI(req.uri)
  override def headers: Seq[(String, String)] =
    _headers.entries.asScala.iterator.map({ case e => (e.getKey, e.getValue) }).toList
  override def header(name: String): Option[String] =
    Option(_headers.get(name))
  override def underlying: Any = rc

  private def asInetSocketAddress(address: SocketAddress): InetSocketAddress =
    InetSocketAddress.createUnresolved(address.host, address.port)
}
