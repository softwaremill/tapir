package sttp.tapir.util

import org.scalatest.Matchers._
import scala.tools.reflect.{ToolBox, ToolBoxError}

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
