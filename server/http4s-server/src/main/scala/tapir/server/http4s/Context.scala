package tapir.server.http4s

import org.http4s.{EntityBody, Headers}
import org.http4s.util.CaseInsensitiveString

private[http4s] case class Context[F[_]](queryParams: Map[String, Seq[String]],
                                         headers: Headers,
                                         basicBody: Option[Any],
                                         streamingBody: EntityBody[F],
                                         unmatchedPath: String) {
  def headers(key: String): Seq[String] = {
    val k = CaseInsensitiveString.apply(key)
    headers.filter(_.name == k).map(_.value).toSeq
  }
  def queryParam(name: String): Seq[String] = queryParams.get(name).toSeq.flatten
  def dropPath(n: Int): Context[F] = copy(unmatchedPath = unmatchedPath.drop(n))
}
