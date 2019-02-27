package tapir.server.http4s
import org.http4s.Request
import org.http4s.util.CaseInsensitiveString
import tapir.internal.server.DecodeInputsContext
import tapir.model.Method

class Http4sDecodeInputsContext[F[_]](req: Request[F]) extends DecodeInputsContext {
  override def method: Method = Method(req.method.name.toUpperCase)
  override def nextPathSegment: (Option[String], DecodeInputsContext) = {

    val nextStart = req.uri.path.dropWhile(_ == '/')
    val (segment, rest) = nextStart.split("/", 2) match {
      case Array("")   => (None, "")
      case Array(s)    => (Some(s), "")
      case Array(s, t) => (Some(s), t)
    }

    (segment, new Http4sDecodeInputsContext(req.withUri(req.uri.withPath(rest))))
  }
  override def header(name: String): List[String] = req.headers.get(CaseInsensitiveString(name)).map(_.value).toList
  override def headers: Seq[(String, String)] = req.headers.map(h => (h.name.value, h.value)).toSeq
  override def queryParameter(name: String): Seq[String] = queryParameters.get(name).toList.flatten
  override val queryParameters: Map[String, Seq[String]] = req.multiParams
  override def bodyStream: Any = req.body
}
