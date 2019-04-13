package tapir.generic

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object MethodNameMacro {
  // http://onoffswitch.net/extracting-scala-method-names-objects-macros/
  implicit def methodName[A](extractor: A => Any): MethodName = macro methodNamesMacro[A]

  def methodNamesMacro[A: c.WeakTypeTag](c: blackbox.Context)(extractor: c.Expr[A => Any]): c.Expr[MethodName] = {
    import c.universe._

    @tailrec
    def resolveFunctionName(f: Function): String = {
      f.body match {
        // the function name
        case t: Select => t.name.decoded

        case t: Function =>
          resolveFunctionName(t)

        // an application of a function and extracting the name
        case t: Apply if t.fun.isInstanceOf[Select] =>
          t.fun.asInstanceOf[Select].name.decoded

        // curried lambda
        case t: Block if t.expr.isInstanceOf[Function] =>
          val func = t.expr.asInstanceOf[Function]

          resolveFunctionName(func)

        case _ => {
          throw new RuntimeException("Unable to resolve function name for expression: " + f.body)
        }
      }
    }

    val name = resolveFunctionName(extractor.tree.asInstanceOf[Function])

    val literal = c.Expr[String](Literal(Constant(name)))

    reify {
      MethodName(literal.splice)
    }
  }
}

case class MethodName(name: String)
