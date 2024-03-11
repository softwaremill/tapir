package sttp.tapir.server.vertx.decoders

import java.net.InetSocketAddress
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.vertx.routing.MethodMapping

import scala.collection.immutable._
import scala.collection.JavaConverters._

private[vertx] case class VertxServerRequest(rc: RoutingContext, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  lazy val connectionInfo: ConnectionInfo = {
    val conn = rc.request.connection
    ConnectionInfo(
      Option(conn.localAddress).map(asInetSocketAddress),
      Option(conn.remoteAddress).map(asInetSocketAddress),
      Option(conn.isSsl)
    )
  }
  override lazy val method: Method = MethodMapping.vertxToSttp(rc.request)
  override lazy val protocol: String = Option(rc.request.scheme).getOrElse("")
  override lazy val uriStr: String = rc.request.uri
  override lazy val uri: Uri = Uri.unsafeParse(rc.request.uri)
  override lazy val headers: Seq[Header] = rc.request.headers.entries.asScala.iterator.map(e => Header(e.getKey, e.getValue)).toList
  override lazy val queryParameters: QueryParams = Uri.unsafeParse(rc.request.uri()).params
  override lazy val pathSegments: List[String] = {
    val segments = uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList
    if (segments == List("")) Nil else segments // representing the root path as an empty list
  }

  override def underlying: Any = rc

  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): VertxServerRequest = copy(attributes = attributes.put(k, v))

  private def asInetSocketAddress(address: SocketAddress): InetSocketAddress =
    InetSocketAddress.createUnresolved(address.host, address.port)

  override def withUnderlying(underlying: Any): ServerRequest =
    new VertxServerRequest(rc = underlying.asInstanceOf[RoutingContext], attributes)
}
