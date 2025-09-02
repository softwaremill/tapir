package sttp.tapir.client.tests

import scala.concurrent.ExecutionContext

object ClientTestsPlatform {
  // Inspired by the default ExecutionContext implementation in MUnit, MUnit works fine on Native
  val executionContext: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

  val platformIsScalaJS: Boolean = false
  val platformIsScalaNative: Boolean = true
}
