package sttp.tapir.internal

private[tapir] object Debug {
  import scala.reflect.macros.blackbox

  private val macroDebugEnabled = System.getenv("TAPIR_LOG_GENERATED_CODE") == "true"

  def logGeneratedCode(c: blackbox.Context)(typeName: String, tree: c.universe.Tree): Unit = {
    import c.universe._
    if (macroDebugEnabled) {
      println(s"""$typeName macro output start:""")
      println(showCode(tree))
      println(s"""$typeName macro output end.""")
    }
  }
}
