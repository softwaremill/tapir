package sttp.tapir.client.tests

import scala.concurrent.ExecutionContext

object ClientTestsPlatform {
  // Using the default ScalaTest execution context seems to cause issues on JS.
  // https://github.com/scalatest/scalatest/issues/1039
  val executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  // val ioRT: IORuntime = cats.effect.unsafe.implicits.global

  val platformIsScalaJS: Boolean = true
  val platformIsScalaNative: Boolean = false
}
