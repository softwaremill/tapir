package sttp.tapir.server.play

import akka.stream.Materializer
import play.api.mvc.RequestHeader
import sttp.model.{Method, MultiQueryParams}
import sttp.tapir.server.internal.DecodeInputsContext
import sttp.tapir.model.ServerRequest

private[play] class PlayDecodeInputContext(request: RequestHeader, pathConsumed: Int = 0, serverOptions: PlayServerOptions)(
    implicit mat: Materializer
) extends DecodeInputsContext {
  override def method: Method = Method(request.method.toUpperCase())

  override def nextPathSegment: (Option[String], DecodeInputsContext) = {
    val path = request.path.drop(pathConsumed)
    val nextStart = path.dropWhile(_ == '/')
    val segment = nextStart.split("/", 2) match {
      case Array("")   => None
      case Array(s)    => Some(s)
      case Array(s, _) => Some(s)
    }
    val charactersConsumed = segment.map(_.length).getOrElse(0) + (path.length - nextStart.length)

    (segment, new PlayDecodeInputContext(request, pathConsumed + charactersConsumed, serverOptions))
  }
  override def header(name: String): List[String] = request.headers.toMap.get(name).toList.flatten
  override def headers: Seq[(String, String)] = request.headers.headers
  override def queryParameter(name: String): Seq[String] = request.queryString.get(name).toSeq.flatten
  override def queryParameters: MultiQueryParams = MultiQueryParams.fromMultiMap(request.queryString)
  override def bodyStream: Any = throw new UnsupportedOperationException("Play doesn't support request body streaming")

  override def serverRequest: ServerRequest = new PlayServerRequest(request)
}
