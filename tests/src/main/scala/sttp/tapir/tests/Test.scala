package sttp.tapir.tests

import org.scalactic.source.Position
import org.scalatest.Assertion

import scala.concurrent.Future

class Test(val name: String, val f: () => Future[Assertion], val pos: Position)
object Test {
  def apply(name: String)(f: => Future[Assertion])(implicit pos: Position): Test = new Test(name, () => f, pos)
}
