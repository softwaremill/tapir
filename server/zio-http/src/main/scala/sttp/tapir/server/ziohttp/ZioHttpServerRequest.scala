package sttp.tapir.server.ziohttp

import sttp.model.{QueryParams, Uri, Header => SttpHeader, Method => SttpMethod}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import zhttp.http.Request

import java.net.InetSocketAddress
import scala.collection.immutable.Seq

class ZioHttpServerRequest(req: Request) extends ServerRequest {
  override def protocol: String = "HTTP/1.1" //TODO: missing field in request

  def remote: Option[InetSocketAddress] =
    for {
      host <- req.url.host
      port <- req.url.port
    } yield new InetSocketAddress(host, port)

  override lazy val connectionInfo: ConnectionInfo =
    ConnectionInfo(None, remote, None)

  override def underlying: Any = req

  override lazy val pathSegments: List[String] = req.url.path.toList

  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(req.url.queryParams)

  override def method: SttpMethod = SttpMethod(req.method.asJHttpMethod.name().toUpperCase)

  override def uri: Uri = Uri.unsafeParse(req.url.toString)

  override lazy val headers: Seq[SttpHeader] = req.headers.map(h => SttpHeader(h.name.toString, h.value.toString))
}
