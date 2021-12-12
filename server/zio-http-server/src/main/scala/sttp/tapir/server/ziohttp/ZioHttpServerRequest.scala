package sttp.tapir.server.ziohttp

import sttp.model.{QueryParams, Uri, Header => SttpHeader, Method => SttpMethod}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import zhttp.http.Request

import java.net.InetSocketAddress
import scala.collection.immutable.Seq

class ZioHttpServerRequest(req: Request) extends ServerRequest {
  override def protocol: String = "HTTP/1.1" // missing field in request

  private def remote: Option[InetSocketAddress] =
    for {
      host <- req.url.host
      port <- req.url.port
    } yield new InetSocketAddress(host, port)

  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, remote, None)
  override def underlying: Any = req
  override lazy val pathSegments: List[String] = req.url.path.toList
  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(req.url.queryParams)
  override lazy val method: SttpMethod = SttpMethod(req.method.asHttpMethod.name().toUpperCase)
  override lazy val uri: Uri = Uri.unsafeParse(req.url.toString)
  override lazy val headers: Seq[SttpHeader] = req.getHeaders.map(h => SttpHeader(h.name.toString, h.value.toString))
}
