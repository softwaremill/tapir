package tapir.generic

import tapir.Schema

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

object OneOfMacro {
  // http://onoffswitch.net/extracting-scala-method-names-objects-macros/

  def oneOfMacro[E: c.WeakTypeTag, V: c.WeakTypeTag](
      c: blackbox.Context
  )(extractor: c.Expr[E => V], asString: c.Expr[V => String])(mapping: c.Expr[(V, Schema[_])]*): c.Expr[Schema[E]] = {
    import c.universe._

    @tailrec
    def resolveFunctionName(f: Function): String = {
      f.body match {
        // the function name
        case t: Select => t.name.decodedName.toString

        case t: Function =>
          resolveFunctionName(t)

        // an application of a function and extracting the name
        case t: Apply if t.fun.isInstanceOf[Select @unchecked] =>
          t.fun.asInstanceOf[Select].name.decodedName.toString

        // curried lambda
        case t: Block if t.expr.isInstanceOf[Function @unchecked] =>
          val func = t.expr.asInstanceOf[Function]

          resolveFunctionName(func)

        case _ =>
          throw new RuntimeException("Unable to resolve function name for expression: " + f.body)
      }
    }
    val weakTypeE = weakTypeOf[E]

    def extractTypeArguments(weakType: c.Type): List[String] = {
      def allTypeArguments(tn: c.Type): Seq[c.Type] = tn.typeArgs.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
      allTypeArguments(weakType).map(_.typeSymbol.name.decodedName.toString).toList
    }

    val name = resolveFunctionName(extractor.tree.asInstanceOf[Function])
    val schemaForE =
      q"""{
            import tapir.Schema._
            import tapir.SchemaType._
            val rawMapping = scala.collection.immutable.Map(..$mapping)
            val discriminator = Discriminator($name, rawMapping.map{case (k, sf)=> $asString.apply(k) -> SRef(sf.schemaType.asInstanceOf[SObject].info)})
            Schema(SCoproduct(SObjectInfo(${weakTypeE.typeSymbol.fullName},${extractTypeArguments(weakTypeE)}), rawMapping.values.toList, Some(discriminator)))
          }"""

    Debug.logGeneratedCode(c)(weakTypeE.typeSymbol.fullName, schemaForE)
    c.Expr[Schema[E]](schemaForE)
  }
}
