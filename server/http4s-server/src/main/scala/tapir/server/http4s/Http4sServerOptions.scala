package tapir.server.http4s
import scala.concurrent.ExecutionContext

case class Http4sServerOptions(blockingExecutionContext: ExecutionContext)

object Http4sServerOptions {
  implicit val Default: Http4sServerOptions = Http4sServerOptions(ExecutionContext.Implicits.global)
}
