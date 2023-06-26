package sttp.tapir.server.jdkhttp.internal

import com.sun.net.httpserver.HttpExchange
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}
import sttp.tapir.{AttributeKey, AttributeMap}

import scala.jdk.CollectionConverters._

private[jdkhttp] case class JdkHttpServerRequest(
    exchange: HttpExchange,
    attributes: AttributeMap = AttributeMap.Empty
) extends ServerRequest {
  override def protocol: String = exchange.getProtocol

  override def connectionInfo: ConnectionInfo =
    ConnectionInfo(
      Some(exchange.getRemoteAddress),
      Some(exchange.getLocalAddress),
      None
    )

  override def underlying: Any = exchange

  override def pathSegments: List[String] =
    uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList

  override def queryParameters: QueryParams = uri.params

  override def method: Method = Method.unsafeApply(exchange.getRequestMethod)

  override lazy val uri: Uri = Uri.unsafeParse(exchange.getRequestURI.toString)

  override def headers: Seq[Header] =
    exchange.getRequestHeaders
      .entrySet()
      .asScala
      .toList
      .flatMap { h =>
        h.getValue.asScala.map { v =>
          Header.unsafeApply(h.getKey, v)
        }
      }

  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)

  override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = copy(attributes = attributes.put(k, v))

  override def withUnderlying(underlying: Any): ServerRequest = {
    require(underlying.isInstanceOf[HttpExchange])
    JdkHttpServerRequest(underlying.asInstanceOf[HttpExchange], attributes)
  }
}
