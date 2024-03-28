package sttp.tapir.server.netty.cats.internal

import scala.concurrent.ExecutionContext

object ExecutionContexts {
  val sameThread: ExecutionContext = new ExecutionContext {
    override def execute(runnable: Runnable): Unit = runnable.run()

    override def reportFailure(cause: Throwable): Unit =
      ExecutionContext.defaultReporter(cause)
  }
}
