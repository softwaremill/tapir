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

  println("created JdkHttpServerRequest")

  def wrap[A](name: String, a: => A): A = {
    println(s"Got to $name")
    val r = a
    println(s"Evaluated $name, got $r")
    r
  }

  override def protocol: String = wrap("protocol", exchange.getProtocol)

  override def connectionInfo: ConnectionInfo = wrap(
    "connection info",
    ConnectionInfo(
      Some(exchange.getRemoteAddress),
      Some(exchange.getLocalAddress),
      None
    )
  )

  override def underlying: Any = wrap("underlying", exchange)

  override def pathSegments: List[String] = {
    println(s"raw path: ${exchange.getRequestURI.getPath}")
    wrap("pathSegments", uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList)
  }

  override def queryParameters: QueryParams = wrap("query params", uri.params)

  override def method: Method = wrap("method", Method.unsafeApply(exchange.getRequestMethod))

  override lazy val uri: Uri = wrap("uri", Uri.unsafeParse(exchange.getRequestURI.toString))

  override def headers: Seq[Header] = wrap(
    "headers", {
      exchange.getRequestHeaders // TODO read javadoc to understand the below TODO
        .entrySet()
        .asScala
        .map { h =>
          Header.unsafeApply(h.getKey(), h.getValue().asScala.mkString("; ")) // TODO this is most likely wrong
        }
        .toList
    }
  )

  override def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)

  override def attribute[T](k: AttributeKey[T], v: T): ServerRequest = copy(attributes = attributes.put(k, v))

  override def withUnderlying(underlying: Any): ServerRequest = {
    require(underlying.isInstanceOf[HttpExchange])
    JdkHttpServerRequest(underlying.asInstanceOf[HttpExchange], attributes)
  }
}
