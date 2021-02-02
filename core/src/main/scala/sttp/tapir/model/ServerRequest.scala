package sttp.tapir.model

import java.net.{InetSocketAddress, URI}

import sttp.model.Method

trait ServerRequest {
  def method: Method
  def protocol: String
  def uri: URI
  def connectionInfo: ConnectionInfo
  def headers: Seq[(String, String)]
  def header(name: String): Option[String]
  def underlying: Any
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])
