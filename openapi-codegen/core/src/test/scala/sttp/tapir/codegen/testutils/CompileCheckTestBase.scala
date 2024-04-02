package sttp.tapir.codegen.testutils

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
    compile {
      val (pkgLines, rest) = code.linesIterator.partition(_.trim.startsWith("package"))
      // Need to strip the package prefix for references since removing decl
      val pkgNames = pkgLines.map(s => s.stripPrefix("package").trim).toSeq.distinct
      assert(pkgNames.size <= 1, "output was split into more than one package")
      pkgNames.headOption.foldLeft(rest.mkString("\n"))((body, pkg) => body.replaceAll(s"${pkg}.", ""))
    }
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
