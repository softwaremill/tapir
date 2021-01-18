package sttp.tapir.generic.internal

import sttp.tapir.Schema

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

    // FIXME: Despite the imports, `Discriminator`, `Schema`, `SCoproduct`, `SRef` and `SObject` should be fully qualified from `_root_`.
    // I think that even with these imports, terms defined in the same scope with the same names will be given priority
    // over the imported (and intended) ones. Someone will need to find where each of these types lives.
    val schemaForE =
      q"""{
            import _root_.sttp.tapir.Schema._
            import _root_.sttp.tapir.SchemaType._
            val rawMapping = _root_.scala.collection.immutable.Map(..$mapping)
            val discriminator = Discriminator($name, rawMapping.map{case (k, sf)=> $asString.apply(k) -> SRef(sf.schemaType.asInstanceOf[SObject].info)})
            Schema(SCoproduct(SObjectInfo(${weakTypeE.typeSymbol.fullName},${extractTypeArguments(weakTypeE)}), rawMapping.values.toList, _root_.scala.Some(discriminator)))
          }"""

    Debug.logGeneratedCode(c)(weakTypeE.typeSymbol.fullName, schemaForE)
    c.Expr[Schema[E]](schemaForE)
  }
}
