package sttp.tapir.server.netty.cats.internal

import scala.concurrent.ExecutionContext

object ExecutionContexts {
  val sameThread: ExecutionContext = ExecutionContext.parasitic
}
