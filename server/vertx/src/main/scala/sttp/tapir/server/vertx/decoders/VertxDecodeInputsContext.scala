package sttp.tapir.server.vertx.decoders

import io.vertx.core.buffer.Buffer
import io.vertx.scala.core.streams.ReadStream
import io.vertx.scala.ext.web.RoutingContext
import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext
import sttp.tapir.server.vertx.routing.MethodMapping

private[vertx] class VertxDecodeInputsContext(rc: RoutingContext, pathConsumed: Int = 0) extends DecodeInputsContext {
  private lazy val request = rc.request
  private lazy val _headers = request.headers
  private lazy val params = request.params
  override def method: Method = MethodMapping.vertxToSttp(rc.request)
  override def nextPathSegment: (Option[String], DecodeInputsContext) = {
    val path = request.path.get.drop(pathConsumed)
    val nextStart = path.dropWhile(_ == '/')
    val segment = nextStart.split("/", 2) match {
      case Array("")   => None
      case Array(s)    => Some(s)
      case Array(s, _) => Some(s)
    }
    val charactersConsumed = segment.map(_.length).getOrElse(0) + (path.length - nextStart.length)
    (segment, new VertxDecodeInputsContext(rc, pathConsumed + charactersConsumed))
  }
  override def header(name: String): List[String] = request.headers.getAll(name).toList
  override def headers: Seq[(String, String)] = _headers.names.map { key => (key, _headers.get(key).get) }.toSeq
  override def queryParameter(name: String): Seq[String] = params.getAll(name)
  override def queryParameters: MultiQueryParams = MultiQueryParams.fromMultiMap(
    params.names.map { key => (key, params.getAll(key)) }.toMap
  )
  override def bodyStream: Any =
    rc.request.asInstanceOf[ReadStream[Buffer]]
  override def serverRequest: ServerRequest = new VertxServerRequest(rc)
}
