package codegen.testutils

import org.scalactic.source
import org.scalatest.exceptions.{StackDepthException, TestFailedException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import scala.util.Try

trait CompileCheckTestBase extends AnyFlatSpec with Matchers with Checkers {

  def compile(code: String): Try[Unit] = {
    import scala.reflect.runtime.universe._
    import scala.tools.reflect.ToolBox
    util
      .Try {
        val tb = runtimeMirror(this.getClass.getClassLoader).mkToolBox()
        val tree = tb.parse(code)
        tb.compile(tree)
      }
      .map(_ => ())
  }

  def compileWithoutHeader(code: String): Try[Unit] = {
    compile(code.linesIterator.filter(!_.trim.startsWith("package")).mkString("\n"))
  }

  implicit class StringShouldCompileHelper(code: String)(implicit pos: source.Position) {
    def shouldCompile(): Unit = {
      compileWithoutHeader(code) match {
        case util.Success(_) =>
          ()
        case util.Failure(ex) =>
          throw new TestFailedException(
            (_: StackDepthException) => Some(s"The input strings not compiles; ${ex.getMessage}"),
            Some(ex),
            pos
          )
      }
    }
  }
}
