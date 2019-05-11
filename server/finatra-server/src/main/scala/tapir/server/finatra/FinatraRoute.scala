package tapir.server.finatra
import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Future

case class FinatraRoute(handler: Request => Future[Response], path: String)
