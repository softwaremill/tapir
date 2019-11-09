package sttp.tapir.server

import scala.concurrent.ExecutionContext
import cats.effect.Blocker

package object http4s extends TapirHttp4sServer {
  implicit def executionContextToBlocker(ec: ExecutionContext): Blocker =
    Blocker.liftExecutionContext(ec)
}
