package sttp.tapir.server.zhttp

import sttp.model.{QueryParams, Uri, Header => SttpHeader, Method => SttpMethod}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import zhttp.http.Request

import java.net.InetSocketAddress
import scala.collection.immutable.Seq

class ZHttpServerRequest(req: Request) extends ServerRequest {
  def protocol: String = "HTTP/1.1" //TODO: missing field in request

  def remote: Option[InetSocketAddress] =
    for {
      host <- req.url.host
      port <- req.url.port
    } yield new InetSocketAddress(host, port)

  lazy val connectionInfo: ConnectionInfo =
    ConnectionInfo(None, remote, None)

  def underlying: Any = req

  lazy val pathSegments: List[String] = req.url.path.toList

  lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(req.url.queryParams)

  def method: SttpMethod = SttpMethod(req.method.asJHttpMethod.name().toUpperCase)

  def uri: Uri = Uri.unsafeParse(req.url.toString)

  lazy val headers: Seq[SttpHeader] = req.headers.map(h => SttpHeader(h.name.toString, h.value.toString))
}






