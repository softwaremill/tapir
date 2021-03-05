package sttp.tapir.server.vertx.decoders

import io.netty.handler.codec.http.QueryStringDecoder
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.RoutingContext
import sttp.model.{Method, QueryParams}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext
import sttp.tapir.server.vertx.routing.MethodMapping
import sttp.tapir.server.vertx.streams.ReadStreamCompatible

import scala.collection.JavaConverters._

private[vertx] class VertxDecodeInputsContext[S: ReadStreamCompatible](
    rc: RoutingContext,
    pathConsumed: Int = 0
) extends DecodeInputsContext {
  private lazy val request = rc.request
  private lazy val _headers = request.headers
  private lazy val params = request.params
  override def method: Method = MethodMapping.vertxToSttp(rc.request)
  override def nextPathSegment: (Option[String], DecodeInputsContext) = {
    val path = Option(request.path).getOrElse("").drop(pathConsumed)
    val nextStart = path.dropWhile(_ == '/')
    val segment = nextStart.split("/", 2) match {
      case Array("")   => None
      case Array(s)    => Some(s)
      case Array(s, _) => Some(s)
    }
    val charactersConsumed = segment.map(_.length).getOrElse(0) + (path.length - nextStart.length)

    (segment.map(QueryStringDecoder.decodeComponent), new VertxDecodeInputsContext(rc, pathConsumed + charactersConsumed))
  }
  override def header(name: String): List[String] = request.headers.getAll(name).asScala.toList
  override def headers: Seq[(String, String)] =
    _headers.entries.asScala.iterator.map({ case e => (e.getKey, e.getValue) }).toList
  override def queryParameter(name: String): Seq[String] = params.getAll(name).asScala.toList
  override def queryParameters: QueryParams =
    QueryParams.fromMultiMap(
      params.names.asScala.map { key => (key, params.getAll(key).asScala.toList) }.toMap
    )
  override def bodyStream: Any =
    ReadStreamCompatible[S].fromReadStream(rc.request.asInstanceOf[ReadStream[Buffer]])
  override def serverRequest: ServerRequest = new VertxServerRequest(rc)
}
