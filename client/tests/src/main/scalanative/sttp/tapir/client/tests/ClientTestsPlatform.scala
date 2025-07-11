package sttp.tapir.client.tests

// import cats.effect.unsafe.IORuntime
import scala.concurrent.ExecutionContext

object ClientTestsPlatform {
  // These two lines should define IO Runtime and ExcecutionContext on Scala Native, but ScalaTest AsyncFunSuite fails with Timeout exception
  // val ioRT:IORuntime = cats.effect.unsafe.implicits.global
  // val executionContext: ExecutionContext = ioRT.compute

  // Inspired by the default ExecutionContext implementation in MUnit, MUnit works fine on Native
  val executionContext: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }
  // private val globalRT: IORuntime = cats.effect.unsafe.implicits.global
  // val ioRT: IORuntime = IORuntime.apply(executionContext, executionContext, globalRT.scheduler, globalRT.shutdown, globalRT.config)

  val platformIsScalaJS: Boolean = false
  val platformIsScalaNative: Boolean = true
}
