package tapir.model

import java.net.{InetSocketAddress, URI}

trait ServerRequest {
  def method: Method
  def protocol: String
  def uri: URI
  def connectionInfo: ConnectionInfo
  def headers: Seq[(String, String)]
  def header(name: String): Option[String]
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])
