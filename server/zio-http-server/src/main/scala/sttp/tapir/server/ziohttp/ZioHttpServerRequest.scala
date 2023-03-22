package sttp.tapir.server.ziohttp

import sttp.model.{QueryParams, Uri, Header => SttpHeader, Method => SttpMethod}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import zio.http.Path.Segment
import zio.http.Request

import java.net.InetSocketAddress
import scala.collection.immutable.Seq

case class ZioHttpServerRequest(req: Request, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override def protocol: String = "HTTP/1.1" // missing field in request

  private def remote: Option[InetSocketAddress] =
    for {
      host <- req.url.host
      port <- req.url.port
    } yield new InetSocketAddress(host, port)

  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, remote, None)
  override def underlying: Any = req
  override lazy val pathSegments: List[String] = req.url.path.segments.flatMap {
    case Segment.Text(text) => List(text)
    case Segment.Root       => Nil
  }.toList
  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(req.url.queryParams.toMap)
  override lazy val method: SttpMethod = SttpMethod(req.method.name.toUpperCase)
  override lazy val uri: Uri = Uri.unsafeParse(req.url.encode)
  override lazy val headers: Seq[SttpHeader] = req.headers.toList.map { h => SttpHeader(h.key.toString, h.value.toString) }
  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): ZioHttpServerRequest = copy(attributes = attributes.put(k, v))
  override def withUnderlying(underlying: Any): ServerRequest = ZioHttpServerRequest(req = underlying.asInstanceOf[Request], attributes)
}
