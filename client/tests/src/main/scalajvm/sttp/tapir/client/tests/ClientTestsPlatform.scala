package sttp.tapir.client.tests

import cats.effect.unsafe.IORuntime
import scala.concurrent.ExecutionContext

object ClientTestsPlatform {
  // Using the default ScalaTest execution context seems to cause issues on JS.
  // https://github.com/scalatest/scalatest/issues/1039
  val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  val platformIsScalaJS: Boolean = false
  val platformIsScalaNative: Boolean = false
}
