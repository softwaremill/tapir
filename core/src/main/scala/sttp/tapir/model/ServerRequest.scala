package sttp.tapir.model

import sttp.model.headers.Accepts
import sttp.model.{ContentTypeRange, MediaType, QueryParams, RequestMetadata}

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
  lazy val contentTypeParsed: Option[MediaType] = contentType.flatMap(MediaType.parse(_).toOption)

  /** Create a copy of this server request, which reads data from the given underlying implementation. The type of `underlying` should be
    * the same as the type of `this.underlying`.
    */
  def withUnderlying(underlying: Any): ServerRequest
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])

object ConnectionInfo {

  /** When no info can be obtained from context. */
  val NoInfo: ConnectionInfo = ConnectionInfo(None, None, None)
}
