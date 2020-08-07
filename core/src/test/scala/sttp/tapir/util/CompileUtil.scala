package sttp.tapir.util

import matchers.should.Matchers._
import scala.tools.reflect.{ToolBox, ToolBoxError}
import org.scalatest.matchers

object CompileUtil {
  def interceptEval(code: String): ToolBoxError = {
    intercept[ToolBoxError](eval(code))
  }

  def eval(code: String): Any = {
    val tb = mkToolbox()
    tb.eval(tb.parse(code))
  }

  def mkToolbox(compileOptions: String = ""): ToolBox[_ <: scala.reflect.api.Universe] = {
    val m = scala.reflect.runtime.currentMirror
    m.mkToolBox(options = compileOptions)
  }
}
