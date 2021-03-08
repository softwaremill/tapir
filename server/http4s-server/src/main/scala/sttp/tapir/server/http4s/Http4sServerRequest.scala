package sttp.tapir.server.http4s

import org.http4s.Request
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.annotation.tailrec
import scala.collection.immutable.Seq

private[http4s] class Http4sServerRequest[F[_]](req: Request[F]) extends ServerRequest {
  override def protocol: String = req.httpVersion.toString()
  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(req.server, req.remote, req.isSecure)
  override def underlying: Any = req

  /** Can differ from `uri.path`, if the endpoint is deployed in a context */
  override lazy val pathSegments: List[String] = {
    @tailrec
    def run(p: String, acc: List[String]): List[String] = p.dropWhile(_ == '/').split("/", 2) match {
      case Array("")   => acc.reverse
      case Array(s)    => (s :: acc).reverse
      case Array(s, t) => run(t, s :: acc)
    }

    // if the routes are mounted within a context (e.g. using a router), we have to match against what comes
    // after the context. This information is stored in the the PathInfoCaret attribute
    run(req.pathInfo, Nil).map(org.http4s.Uri.decode(_))
  }

  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(req.multiParams)

  override def method: Method = Method(req.method.name.toUpperCase)
  override def uri: Uri = Uri.unsafeParse(req.uri.toString())
  override lazy val headers: Seq[Header] = req.headers.toList.map(h => Header(h.name.value, h.value))
}
