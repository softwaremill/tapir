package sttp.tapir.swagger

import scala.concurrent.ExecutionContext

package object http4s {
  implicit def executionContextToBlocker(ec: ExecutionContext): Blocker =
    Blocker.liftExecutionContext(ec)
}
