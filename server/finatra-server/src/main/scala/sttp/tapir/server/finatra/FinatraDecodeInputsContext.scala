package sttp.tapir.server.finatra

import com.twitter.finagle.http.Request
import io.netty.handler.codec.http.QueryStringDecoder
import sttp.model.{Method, QueryParams}
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext

class FinatraDecodeInputsContext(request: Request, pathConsumed: Int = 0) extends DecodeInputsContext {
  override def method: Method = Method(request.method.toString.toUpperCase)

  override def nextPathSegment: (Option[String], DecodeInputsContext) = {
    val path = request.path.drop(pathConsumed)
    val nextStart = path.dropWhile(_ == '/')
    val segment = nextStart.split("/", 2) match {
      case Array("")   => None
      case Array(s)    => Some(s)
      case Array(s, _) => Some(s)
    }
    val charactersConsumed = segment.map(_.length).getOrElse(0) + (path.length - nextStart.length)

    (segment.map(QueryStringDecoder.decodeComponent), new FinatraDecodeInputsContext(request, pathConsumed + charactersConsumed))
  }
  override def header(name: String): List[String] = request.headerMap.getAll(name).toList
  override def headers: Seq[(String, String)] = request.headerMap.toList
  override def queryParameter(name: String): Seq[String] = request.params.getAll(name).toSeq
  override def queryParameters: QueryParams = QueryParams.fromMultiMap(request.params.toList.groupBy(_._1).mapValues(_.map(_._2)))
  override def bodyStream: Any = request.content
  override def request: ServerRequest = new FinatraServerRequest(request)
}
