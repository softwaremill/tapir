package tapir.generic

object Debug {

  import scala.reflect.macros.blackbox
  import scala.reflect.runtime.universe.{TypeTag, typeOf}

  private val macroDebugEnabled = System.getenv("TAPIR_LOG_GENERATED_CODE") == "true"

  def logGeneratedCode[E: c.WeakTypeTag](c: blackbox.Context)(tree: c.universe.Tree): Unit = {
    if (macroDebugEnabled) {
      println(s"""Generated schema code for ${name[E]}:""")
      println(tree)
    }
  }

  private def name[T: TypeTag] = typeOf[T].typeSymbol.name.toString
}
