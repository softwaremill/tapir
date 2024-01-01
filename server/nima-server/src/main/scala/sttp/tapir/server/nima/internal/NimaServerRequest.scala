package sttp.tapir.server.nima.internal

import io.helidon.webserver.http.{ServerRequest => JavaNimaServerRequest}
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import java.net.{InetSocketAddress, SocketAddress}

import scala.jdk.CollectionConverters.IterableHasAsScala

private[nima] case class NimaServerRequest(r: JavaNimaServerRequest, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override def protocol: String = r.prologue().protocol()
  override def connectionInfo: ConnectionInfo =
    ConnectionInfo(toInetSocketAddress(r.localPeer().address()), toInetSocketAddress(r.remotePeer().address()), Some(r.isSecure))
  override def underlying: Any = r
  override def pathSegments: List[String] = uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList
  override def queryParameters: QueryParams = uri.params
  override def method: Method = Method.unsafeApply(r.prologue().method().text())

  override lazy val uri: Uri = {
    val protocol = emptyIfNull(r.prologue().protocol())
    val authority = emptyIfNull(r.authority())
    val path = emptyIfNull(r.path().rawPath())
    val rawQuery = emptyIfNull(r.query().rawValue())
    val query = if (rawQuery.isEmpty) rawQuery else "?" + rawQuery
    val fragment = emptyIfNull(r.prologue().fragment().rawValue())
    Uri.unsafeParse(s"$protocol://$authority$path$query$fragment")
  }

  override def headers: Seq[Header] = r.headers().asScala.toSeq.flatMap(hv => hv.allValues().asScala.map(v => Header(hv.name(), v)))
  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): NimaServerRequest = copy(attributes = attributes.put(k, v))
  override def withUnderlying(underlying: Any): ServerRequest =
    NimaServerRequest(r = underlying.asInstanceOf[JavaNimaServerRequest], attributes)

  private def toInetSocketAddress(sa: SocketAddress): Option[InetSocketAddress] = sa match {
    case isa: InetSocketAddress => Some(isa)
    case _                      => None
  }

  private def emptyIfNull(s: String): String = if (s == null) "" else s
}
