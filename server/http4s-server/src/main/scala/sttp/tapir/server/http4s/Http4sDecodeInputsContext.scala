package sttp.tapir.server.http4s

import org.http4s.Request
import org.http4s.util.CaseInsensitiveString
import sttp.model.Method
import sttp.tapir.model.ServerRequest
import sttp.tapir.server.internal.DecodeInputsContext

class Http4sDecodeInputsContext[F[_]](req: Request[F]) extends DecodeInputsContext {
  override def method: Method = Method(req.method.name.toUpperCase)
  override def nextPathSegment: (Option[String], DecodeInputsContext) = {
    val nextStart = req.pathInfo.dropWhile(_ == '/')
    val segment = nextStart.split("/", 2) match {
      case Array("")   => None
      case Array(s)    => Some(s)
      case Array(s, _) => Some(s)
    }

    // if the routes are mounted within a context (e.g. using a router), we have to match against what comes
    // after the context. This information is stored in the the PathInfoCaret attribute
    val oldCaret = req.attributes.lookup(Request.Keys.PathInfoCaret).getOrElse(0)
    val segmentSlashLength = segment.map(_.length).getOrElse(0) + 1
    val reqWithNewCaret = req.withAttribute(Request.Keys.PathInfoCaret, oldCaret + segmentSlashLength)

    (segment, new Http4sDecodeInputsContext(reqWithNewCaret))
  }
  override def header(name: String): List[String] = req.headers.get(CaseInsensitiveString(name)).map(_.value).toList
  override def headers: Seq[(String, String)] = req.headers.toList.map(h => (h.name.value, h.value))
  override def queryParameter(name: String): Seq[String] = queryParameters.get(name).toList.flatten
  override val queryParameters: Map[String, Seq[String]] = req.multiParams
  override def bodyStream: Any = req.body
  override def serverRequest: ServerRequest = new Http4sServerRequest(req)
}
