package tapir.server

import scala.concurrent.ExecutionContext
import cats.effect.Blocker

package object http4s extends TapirHttp4sServer {
  private[http4s] type Error = String

  implicit def executionContextToBlocker(ec: ExecutionContext): Blocker =
    Blocker.liftExecutionContext(ec)
}
