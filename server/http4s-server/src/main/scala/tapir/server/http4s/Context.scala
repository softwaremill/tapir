package tapir.server.http4s

import org.http4s.{EntityBody, Headers}
import org.http4s.util.CaseInsensitiveString

// TODO: instead of a Map[String, String] for query params, use Map[String, Seq[String]]
private[http4s] case class Context[F[_]](queryParams: Map[String, String],
                                         headers: Headers,
                                         basicBody: Option[Any],
                                         streamingBody: EntityBody[F],
                                         unmatchedPath: String) {
  def header(key: String): Option[String] = headers.get(CaseInsensitiveString.apply(key)).map(_.value)
  def queryParam(name: String): Option[String] = queryParams.get(name)
  def dropPath(n: Int): Context[F] = copy(unmatchedPath = unmatchedPath.drop(n))
}
