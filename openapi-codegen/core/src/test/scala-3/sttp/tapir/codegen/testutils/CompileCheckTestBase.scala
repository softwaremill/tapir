package sttp.tapir.codegen.testutils

import org.scalactic.source
import org.scalatest.exceptions.{StackDepthException, TestFailedException}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.Checkers

import scala.util.Try

trait CompileCheckTestBase extends AnyFlatSpec with Matchers with Checkers {
  val isScala3: Boolean = true
  private def engine = dotty.tools.repl.ScriptEngine()
  def compile(code: String): Try[Unit] = util.Try(engine.eval(code)).map(_ => ())

  def compileWithoutHeader(code: String): Try[Unit] = {
    compile {
      val (pkgLines, rest) = code.linesIterator.partition(_.trim.startsWith("package"))
      // Need to strip the package prefix for references since removing decl
      val pkgNames = pkgLines.map(s => s.stripPrefix("package").trim).toSeq.distinct
      assert(pkgNames.size <= 1, "output was split into more than one package")
      pkgNames.headOption.foldLeft(rest.mkString("\n"))((body, pkg) => body.replaceAll(s"${pkg}.", ""))
    }
  }

  def checkShouldCompile(code: String)(implicit pos: source.Position): Unit = {
    compileWithoutHeader(code) match {
      case util.Success(_) =>
        ()
      case util.Failure(ex) =>
        throw new TestFailedException(
          (_: StackDepthException) => Some(s"The input string doesn't compile; ${ex.getMessage}"),
          Some(ex),
          pos
        )
    }
  }
  extension (code: String) {
    def shouldCompile()(implicit pos: source.Position): Unit = checkShouldCompile(code)(pos)
  }
}
