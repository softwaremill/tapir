package sttp.tapir.model

import sttp.model.{QueryParams, RequestMetadata}

import java.net.InetSocketAddress

trait ServerRequest extends RequestMetadata {
  def protocol: String
  def connectionInfo: ConnectionInfo
  def underlying: Any

  /** Can differ from `uri.path`, if the endpoint is deployed in a context */
  def pathSegments: List[String]
  def queryParameters: QueryParams
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])
