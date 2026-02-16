package sttp.tapir.server.ziohttp

import sttp.model.{QueryParams, Uri, Header => SttpHeader, Method => SttpMethod}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import zio.http.{Request, QueryParams => ZQueryParams}

import java.net.InetSocketAddress
import scala.collection.immutable.Seq
import java.nio.charset.Charset
import io.netty.handler.codec.http.QueryStringEncoder
import zio.http.Charsets

case class ZioHttpServerRequest(req: Request, attributes: AttributeMap = AttributeMap.Empty) extends ServerRequest {
  override def protocol: String = "HTTP/1.1" // missing field in request
  override lazy val connectionInfo: ConnectionInfo =
    ConnectionInfo(
      None,
      req.remoteAddress.map(remote => new InetSocketAddress(remote, 0)),
      None
    )
  override def underlying: Any = req
  override lazy val pathSegments: List[String] = uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList
  override lazy val queryParameters: QueryParams = QueryParams.fromMultiMap(req.url.queryParams.map)
  override lazy val method: SttpMethod = SttpMethod(req.method.name.toUpperCase)
  override lazy val showShort: String = s"$method ${encodeQueryParams(req.url.path.encode, req.url.queryParams.normalize, Charsets.Http)}"

  override lazy val uri: Uri = {
    // work-around fo zio-http not decoding path segments properly
    Uri.unsafeParse(
      s"/${req.url.path.segments.toList.mkString("/")}${if (req.url.path.hasTrailingSlash) "/" else ""}${req.url.fragment.map(f => s"#$f").getOrElse("")}${req.url.queryParams.encode}"
    )
  }
  override lazy val headers: Seq[SttpHeader] = req.headers.toList.map { h => SttpHeader(h.headerName, h.renderedValue) }
  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  override def attribute[T](k: AttributeKey[T], v: T): ZioHttpServerRequest = copy(attributes = attributes.put(k, v))
  override def withUnderlying(underlying: Any): ServerRequest = ZioHttpServerRequest(req = underlying.asInstanceOf[Request], attributes)

  // Copied from zio.http.netty.QueryParamEncoder.encode
  private def encodeQueryParams(baseUri: String, queryParams: ZQueryParams, charset: Charset): String = {
    val encoder = new QueryStringEncoder(baseUri, charset)
    queryParams.map.foreach { case (key, values) =>
      if (key != "") {
        if (values.isEmpty) {
          encoder.addParam(key, "")
        } else
          values.foreach(value => encoder.addParam(key, value))
      }
    }

    encoder.toString
  }
}
