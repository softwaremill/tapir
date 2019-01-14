package tapir.server.http4s
import org.http4s.Headers
import org.http4s.util.CaseInsensitiveString

private[http4s] case class Context[F[_]](queryParams: Map[String, String], headers: Headers, body: Option[Any], unmatchedPath: String) {
  def header(key: String): Option[String] = headers.get(CaseInsensitiveString.apply(key)).map(_.value)
  def queryParam(name: String): Option[String] = queryParams.get(name)
  def dropPath(n: Int): Context[F] = copy(unmatchedPath = unmatchedPath.drop(n))
}
