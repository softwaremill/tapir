package sttp.tapir.server.armeria

import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.server.ServiceRequestContext
import java.net.InetSocketAddress
import scala.jdk.CollectionConverters.CollectionHasAsScala
import sttp.model.{Header, Method, QueryParams, Uri}
import sttp.tapir.model.{ConnectionInfo, ServerRequest}

private[armeria] final class ArmeriaServerRequest(ctx: ServiceRequestContext) extends ServerRequest {
  private lazy val request: HttpRequest = ctx.request

  lazy val connectionInfo: ConnectionInfo = {
    val remotePort = ctx.remoteAddress[InetSocketAddress]().getPort
    val clientAddress = InetSocketAddress.createUnresolved(ctx.clientAddress().getHostAddress, remotePort)
    ConnectionInfo(
      Some(ctx.localAddress[InetSocketAddress]()),
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
    // ctx.path() always starts with '/'.
    if (ctx.path() == "/") {
      Nil
    } else {
      // Armeria does not decode '%2F' in a path into '/' by default.
      decodeSlash(ctx.path()).substring(1).split("/").toList
    }
  }

  private def decodeSlash(path: String): String = {
    path.replaceAll("%2[fF]", "/")
  }

  override val queryParameters: QueryParams = {
    val params = ctx.queryParams()

    val builder = Seq.newBuilder[(String, Seq[String])]
    builder.sizeHint(params.size())

    params
      .names()
      .forEach(key => {
        val list = params.getAll(key).asScala.toSeq
        builder.addOne((key, list))
      })

    QueryParams(builder.result())
  }
}
