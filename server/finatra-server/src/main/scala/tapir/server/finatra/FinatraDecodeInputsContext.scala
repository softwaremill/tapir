package tapir.server.finatra
import com.twitter.finagle.http.Request
import tapir.internal.server.DecodeInputsContext
import tapir.model.{Method, ServerRequest}

class FinatraDecodeInputsContext(request: Request) extends DecodeInputsContext {
  override def method: Method = Method(request.method.toString.toUpperCase)

  override def nextPathSegment: (Option[String], DecodeInputsContext) = ???
  override def header(name: String): List[String] = request.headerMap.getAll(name).toList
  override def headers: Seq[(String, String)] = request.headerMap.toList
  override def queryParameter(name: String): Seq[String] = request.params.getAll(name).toSeq
  override def queryParameters: Map[String, Seq[String]] = request.params.toList.groupBy(_._1).mapValues(_.map(_._2))
  override def bodyStream: Any = request.content
  override def serverRequest: ServerRequest = new FinatraServerRequest(request)
}
