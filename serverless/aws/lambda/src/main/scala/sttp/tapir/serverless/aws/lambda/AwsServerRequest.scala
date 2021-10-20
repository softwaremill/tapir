package sttp.tapir.serverless.aws.lambda

import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{AttributeKey, AttributeMap, ConnectionInfo, ServerRequest}

import java.net.{InetSocketAddress, URLDecoder}
import scala.collection.immutable.Seq

private[lambda] class AwsServerRequest(request: AwsRequest, attributeMap: AttributeMap = new AttributeMap()) extends ServerRequest {
  private val sttpUri: Uri = {
    val queryString = if (request.rawQueryString.nonEmpty) "?" + request.rawQueryString else ""
    Uri.unsafeParse(s"$protocol://${request.requestContext.domainName.getOrElse("")}${request.rawPath}$queryString")
  }

  override def protocol: String = request.headers.getOrElse("x-forwarded-proto", "http")
  override def connectionInfo: ConnectionInfo =
    ConnectionInfo(None, Some(InetSocketAddress.createUnresolved(request.requestContext.http.sourceIp, 80)), None)
  override def underlying: Any = request
  override def pathSegments: List[String] = {
    request.rawPath.dropWhile(_ == '/').split("/").toList.map(value => URLDecoder.decode(value, "UTF-8"))
  }
  override def queryParameters: QueryParams = sttpUri.params
  override def method: Method = Method.unsafeApply(request.requestContext.http.method)
  override def uri: Uri = sttpUri
  override def headers: Seq[Header] = request.headers.map { case (n, v) => Header(n, v) }.toList

  override def attribute[T](key: AttributeKey[T]): Option[T] = attributeMap.get(key)
  override def withAttribute[T](key: AttributeKey[T], value: T): ServerRequest = new AwsServerRequest(request, attributeMap.put(key, value))
}
