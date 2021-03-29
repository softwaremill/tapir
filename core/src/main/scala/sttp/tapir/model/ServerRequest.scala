package sttp.tapir.model

import sttp.model.headers.Accepts
import sttp.model.{ContentTypeRange, QueryParams, RequestMetadata}

import java.net.InetSocketAddress
import scala.collection.immutable

trait ServerRequest extends RequestMetadata {
  lazy val ranges: Either[String, immutable.Seq[ContentTypeRange]] = Accepts.parse(headers)

  def protocol: String
  def connectionInfo: ConnectionInfo
  def underlying: Any

  /** Can differ from `uri.path`, if the endpoint is deployed in a context */
  def pathSegments: List[String]
  def queryParameters: QueryParams
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])
