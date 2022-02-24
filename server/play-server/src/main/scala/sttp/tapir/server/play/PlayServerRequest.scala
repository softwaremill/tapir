package sttp.tapir.server.play

import play.api.mvc.RequestHeader
import play.utils.UriEncoding
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import java.nio.charset.StandardCharsets
import scala.collection.immutable._

private[play] class PlayServerRequest(requestHeader: RequestHeader, requestWithContext: RequestHeader) extends ServerRequest {
  override lazy val method: Method = Method(requestHeader.method.toUpperCase)
  override def protocol: String = requestHeader.version
  override lazy val uri: Uri = Uri.unsafeParse(requestHeader.uri)
  override lazy val connectionInfo: ConnectionInfo = ConnectionInfo(None, None, Some(requestHeader.secure))
  override lazy val headers: Seq[Header] = requestHeader.headers.headers.map { case (k, v) => Header(k, v) }.toList
  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(requestHeader.queryString)
  override lazy val pathSegments: List[String] = {
    val segments = requestHeader.path.dropWhile(_ == '/').split("/").toList.map(UriEncoding.decodePathSegment(_, StandardCharsets.UTF_8))
    if (segments == List("")) Nil else segments // representing the root path as an empty list
  }

  override def underlying: Any = requestWithContext
}
