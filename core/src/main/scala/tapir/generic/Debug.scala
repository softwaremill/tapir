package tapir.generic

object Debug {

  import scala.reflect.macros.blackbox
  import scala.reflect.runtime.universe.{TypeTag, typeOf}

  private val macroDebugEnabled = System.getenv("TAPIR_LOG_GENERATED_CODE") == "true"

  def logGeneratedCode[E: c.WeakTypeTag](c: blackbox.Context)(tree: c.universe.Tree): Unit = {
    import c.universe._
    if (macroDebugEnabled) {
      println(s"""${name[E]} macro output start:""")
      println(showCode(tree))
      println(s"""${name[E]} macro output end.""")
    }
  }

  private def name[T: TypeTag] = typeOf[T].typeSymbol.name.toString
}
