package sttp.tapir.server.armeria

import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.server.ServiceRequestContext

import java.net.InetSocketAddress
import scala.collection.JavaConverters._
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.{AttributeKey, AttributeMap}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

import scala.collection.immutable.Seq

private[armeria] final case class ArmeriaServerRequest(ctx: ServiceRequestContext, attributes: AttributeMap = AttributeMap.Empty)
    extends ServerRequest {
  private lazy val request: HttpRequest = ctx.request

  lazy val connectionInfo: ConnectionInfo = {
    val remotePort = ctx.remoteAddress().getPort
    val clientAddress = InetSocketAddress.createUnresolved(ctx.clientAddress().getHostAddress, remotePort)
    ConnectionInfo(
      Some(ctx.localAddress()),
      Some(clientAddress),
      Some(ctx.sessionProtocol().isTls)
    )
  }

  override lazy val method: Method = MethodMapping.fromArmeria(request.method())

  override lazy val protocol: String = ctx.sessionProtocol().toString

  override lazy val uri: Uri = Uri(ctx.request().uri())

  override lazy val headers: Seq[Header] = HeaderMapping.fromArmeria(request.headers())

  override def header(name: String): Option[String] = Option(request.headers().get(name))

  override def underlying: Any = ctx

  override val pathSegments: List[String] = {
    val segments = uri.pathSegments.segments.map(_.v).filter(_.nonEmpty).toList
    if (segments == List("")) Nil else segments // representing the root path as an empty list
  }

  override val queryParameters: QueryParams = {
    val params = ctx.queryParams()

    val builder = Seq.newBuilder[(String, Seq[String])]
    builder.sizeHint(params.size())

    params
      .names()
      .forEach(key => {
        val list = params.getAll(key).asScala.toList
        builder += ((key, list))
      })

    QueryParams(builder.result())
  }

  def attribute[T](k: AttributeKey[T]): Option[T] = attributes.get(k)
  def attribute[T](k: AttributeKey[T], v: T): ArmeriaServerRequest = copy(attributes = attributes.put(k, v))

  override def withUnderlying(underlying: Any): ServerRequest = ArmeriaServerRequest(
    underlying.asInstanceOf[ServiceRequestContext],
    attributes
  )
}
