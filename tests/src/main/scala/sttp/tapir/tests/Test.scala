package sttp.tapir.tests

import org.scalactic.source.Position

class Test(val name: String, val f: () => Unit, val pos: Position)
object Test {
  def apply(name: String)(f: => Unit)(implicit pos: Position): Test = new Test(name, () => f, pos)
}
