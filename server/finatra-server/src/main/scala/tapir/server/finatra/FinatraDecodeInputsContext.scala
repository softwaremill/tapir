package tapir.server.finatra
import tapir.internal.server.DecodeInputsContext
import tapir.model.{Method, ServerRequest}

class FinatraDecodeInputsContext extends DecodeInputsContext {
  override def method: Method = ???
  override def nextPathSegment: (Option[String], DecodeInputsContext) = ???
  override def header(name: String): List[String] = ???
  override def headers: Seq[(String, String)] = ???
  override def queryParameter(name: String): Seq[String] = ???
  override def queryParameters: Map[String, Seq[String]] = ???
  override def bodyStream: Any = ???
  override def serverRequest: ServerRequest = ???
}
