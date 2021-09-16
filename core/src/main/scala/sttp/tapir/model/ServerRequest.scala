package sttp.tapir.model

import sttp.model.headers.Accepts
import sttp.model.{ContentTypeRange, QueryParams, RequestMetadata}

import java.net.InetSocketAddress
import scala.collection.immutable.Seq

trait ServerRequest extends RequestMetadata {
  def protocol: String
  def connectionInfo: ConnectionInfo
  def underlying: Any

  /** Can differ from `uri.path`, if the endpoint is deployed in a context */
  def pathSegments: List[String]
  def queryParameters: QueryParams

  lazy val acceptsContentTypes: Either[String, Seq[ContentTypeRange]] = Accepts.parse(headers)
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])

object ConnectionInfo {

  /** When no info can be obtained from context. */
  val NoInfo: ConnectionInfo = ConnectionInfo(None, None, None)
}
