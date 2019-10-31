package tapir.generic

object Debug {

  import scala.reflect.macros.blackbox
  import scala.reflect.runtime.universe.{WeakTypeTag, weakTypeOf}

  private val macroDebugEnabled = System.getenv("TAPIR_LOG_GENERATED_CODE") == "true"

  def logGeneratedCode[T: c.WeakTypeTag](c: blackbox.Context)(tree: c.universe.Tree): Unit = {
    import c.universe._
    if (macroDebugEnabled) {
      println(s"""${name[T]} macro output start:""")
      println(showCode(tree))
      println(s"""${name[T]} macro output end.""")
    }
  }

  private def name[T: WeakTypeTag] = weakTypeOf[T].typeSymbol.name.toString
}
