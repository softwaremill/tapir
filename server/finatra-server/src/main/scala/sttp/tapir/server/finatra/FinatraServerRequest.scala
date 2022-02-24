package sttp.tapir.server.finatra

import com.twitter.finagle.http.Request
import io.netty.handler.codec.http.QueryStringDecoder
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.collection.immutable.Seq

class FinatraServerRequest(request: Request) extends ServerRequest {
  override lazy val method: Method = Method(request.method.toString.toUpperCase)
  override lazy val protocol: String = request.version.toString
  override lazy val uri: Uri = Uri.unsafeParse(request.uri)
  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, Some(request.remoteSocketAddress), None)
  override lazy val headers: Seq[Header] = request.headerMap.toList.map { case (k, v) => Header(k, v) }
  override lazy val queryParameters: QueryParams =
    QueryParams.fromMultiMap(request.params.keys.toList.map(k => k -> request.params.getAll(k).toList).toMap)
  override lazy val pathSegments: List[String] = {
    val segments = request.path.dropWhile(_ == '/').split("/").toList.map(QueryStringDecoder.decodeComponent)
    if (segments == List("")) Nil else segments // representing the root path as an empty list
  }
  override def underlying: Any = request
}
