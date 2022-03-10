package sttp.tapir.server.vertx.decoders

import io.netty.handler.codec.http.QueryStringDecoder

import java.net.InetSocketAddress
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.server.vertx.routing.MethodMapping

import scala.collection.immutable._
import scala.collection.JavaConverters._

private[vertx] class VertxServerRequest(rc: RoutingContext) extends ServerRequest {
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
  override lazy val uri: Uri = Uri.unsafeParse(rc.request.uri)
  override lazy val headers: Seq[Header] = rc.request.headers.entries.asScala.iterator.map(e => Header(e.getKey, e.getValue)).toList
  override lazy val queryParameters: QueryParams = {
    val params = rc.request.params
    QueryParams.fromMultiMap(
      params.names.asScala.map { key => (key, params.getAll(key).asScala.toList) }.toMap
    )
  }
  override lazy val pathSegments: List[String] = {
    val path = Option(rc.request.path).getOrElse("")
    val segments = path.dropWhile(_ == '/').split("/").toList.map(QueryStringDecoder.decodeComponent)
    if (segments == List("")) Nil else segments // representing the root path as an empty list
  }

  override def underlying: Any = rc

  private def asInetSocketAddress(address: SocketAddress): InetSocketAddress =
    InetSocketAddress.createUnresolved(address.host, address.port)

  override def withUnderlying(underlying: Any): ServerRequest = new VertxServerRequest(rc = underlying.asInstanceOf[RoutingContext])
}
