package tapir.generic

import tapir.Schema.Discriminator
import tapir.SchemaFor

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object OneOfMacro {
  // http://onoffswitch.net/extracting-scala-method-names-objects-macros/

  def oneOfMacro[E: c.WeakTypeTag, V: c.WeakTypeTag](
      c: blackbox.Context
  )(extractor: c.Expr[E => V], asString: c.Expr[V => String])(mapping: c.Expr[(V, SchemaFor[_])]*): c.Expr[Discriminator] = {
    import c.universe._

    @tailrec
    def resolveFunctionName(f: Function): String = {
      f.body match {
        // the function name
        case t: Select => t.name.decodedName.toString

        case t: Function =>
          resolveFunctionName(t)

        // an application of a function and extracting the name
        case t: Apply if t.fun.isInstanceOf[Select] =>
          t.fun.asInstanceOf[Select].name.decodedName.toString

        // curried lambda
        case t: Block if t.expr.isInstanceOf[Function] =>
          val func = t.expr.asInstanceOf[Function]

          resolveFunctionName(func)

        case _ =>
          throw new RuntimeException("Unable to resolve function name for expression: " + f.body)
      }
    }

    val name = resolveFunctionName(extractor.tree.asInstanceOf[Function])
    val discriminator =
      q"""val rawMapping = Map(..$mapping)
         tapir.Schema.Discriminator($name,rawMapping.collect{case (k, sf)=> $asString.apply(k) -> tapir.Schema.SRef(sf.schema.asInstanceOf[tapir.Schema.SObject].info.fullName)})"""
    c.Expr[Discriminator](discriminator)
  }
}
