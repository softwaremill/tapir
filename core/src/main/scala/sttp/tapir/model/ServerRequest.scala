package sttp.tapir.model

import sttp.model.headers.Accepts
import sttp.model.{ContentTypeRange, Header, MediaType, Method, QueryParams, RequestMetadata, Uri}
import sttp.tapir.AttributeKey

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

  def attribute[T](k: AttributeKey[T]): Option[T]
  def attribute[T](k: AttributeKey[T], v: T): ServerRequest

  /** Create a copy of this server request, which reads data from the given underlying implementation. The type of `underlying` should be
    * the same as the type of `this.underlying`.
    */
  def withUnderlying(underlying: Any): ServerRequest

  /** Create a copy of this server request, which overrides some of the data that is read from the underlying implementation with the given
    * values. E.g. instead of reading the headers from the underlying request, the headers might be given explicitly.
    */
  def withOverride(
      methodOverride: Option[Method] = None,
      uriOverride: Option[Uri] = None,
      protocolOverride: Option[String],
      connectionInfoOverride: Option[ConnectionInfo],
      pathSegmentsOverride: Option[List[String]] = None,
      queryParametersOverride: Option[QueryParams] = None,
      headersOverride: Option[Seq[Header]] = None
  ): ServerRequest =
    new ServerRequestOverride(
      methodOverride,
      uriOverride,
      protocolOverride,
      connectionInfoOverride,
      pathSegmentsOverride,
      queryParametersOverride,
      headersOverride,
      this
    )

  /** A short representation of this request, including the request method, path and query. */
  def showShort: String = s"$method ${uri.copy(scheme = None, authority = None, fragmentSegment = None).toString}"
}

class ServerRequestOverride(
    methodOverride: Option[Method],
    uriOverride: Option[Uri],
    protocolOverride: Option[String],
    connectionInfoOverride: Option[ConnectionInfo],
    pathSegmentsOverride: Option[List[String]],
    queryParametersOverride: Option[QueryParams],
    headersOverride: Option[Seq[Header]],
    delegate: ServerRequest
) extends ServerRequest {
  override def method: Method = methodOverride.getOrElse(delegate.method)
  override def uri: Uri = uriOverride.getOrElse(delegate.uri)
  override def protocol: String = protocolOverride.getOrElse(delegate.protocol)
  override def connectionInfo: ConnectionInfo = connectionInfoOverride.getOrElse(delegate.connectionInfo)
  override def underlying: Any = delegate.underlying
  override def pathSegments: List[String] = pathSegmentsOverride.getOrElse(delegate.pathSegments)
  override def queryParameters: QueryParams = queryParametersOverride.getOrElse(delegate.queryParameters)
  override def headers: Seq[Header] = headersOverride.getOrElse(delegate.headers)
  override def attribute[T](k: AttributeKey[T]): Option[T] = delegate.attribute(k)
  override def attribute[T](k: AttributeKey[T], v: T): ServerRequest =
    new ServerRequestOverride(
      methodOverride,
      uriOverride,
      protocolOverride,
      connectionInfoOverride,
      pathSegmentsOverride,
      queryParametersOverride,
      headersOverride,
      delegate.attribute(k, v)
    )
  override def withUnderlying(underlying: Any): ServerRequest =
    new ServerRequestOverride(
      methodOverride,
      uriOverride,
      protocolOverride,
      connectionInfoOverride,
      pathSegmentsOverride,
      queryParametersOverride,
      headersOverride,
      delegate.withUnderlying(underlying)
    )
}

case class ConnectionInfo(local: Option[InetSocketAddress], remote: Option[InetSocketAddress], secure: Option[Boolean])

object ConnectionInfo {

  /** When no info can be obtained from context. */
  val NoInfo: ConnectionInfo = ConnectionInfo(None, None, None)
}
