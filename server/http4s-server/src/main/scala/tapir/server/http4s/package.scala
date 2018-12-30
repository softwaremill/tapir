package tapir.server
import cats.data.StateT
import org.http4s.Headers
import org.http4s.util.CaseInsensitiveString

package object http4s extends Http4sServer {
  private[http4s] case class MatchResult[F[_]](values: List[Any], ctx: Context[F]) {
    def prependValue(v: Any): MatchResult[F] = copy(values = v :: values)
  }

  private[http4s] type Error = String

  private[http4s] type ContextState[F[_]] = StateT[Either[Error, ?], Context[F], MatchResult[F]]

  private[http4s] case class Context[F[_]](queryParams: Map[String, String], headers: Headers, body: Option[Any], unmatchedPath: String) {
    def getHeader(key: String): Option[String] = headers.get(CaseInsensitiveString.apply(key)).map(_.value)
    def getQueryParam(name: String): Option[String] = queryParams.get(name)
    def dropPath(n: Int): Context[F] = copy(unmatchedPath = unmatchedPath.drop(n))
  }
}
