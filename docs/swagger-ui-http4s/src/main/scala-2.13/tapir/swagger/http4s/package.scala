package tapir.swagger

import scala.concurrent.ExecutionContext
import cats.effect.Blocker

package object http4s {
  implicit def executionContextToBlocker(ec: ExecutionContext): Blocker =
    Blocker.liftExecutionContext(ec)
}
