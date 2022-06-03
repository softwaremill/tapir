package sttp.tapir.internal

import sttp.tapir.Schema
import sttp.tapir.generic.Configuration

import scala.annotation.tailrec
import scala.reflect.macros.blackbox

private[tapir] object OneOfMacro {
  // http://onoffswitch.net/extracting-scala-method-names-objects-macros/

  def generateOneOfUsingField[E: c.WeakTypeTag, V: c.WeakTypeTag](
      c: blackbox.Context
  )(extractor: c.Expr[E => V], asString: c.Expr[V => String])(
      mapping: c.Expr[(V, Schema[_])]*
  )(conf: c.Expr[Configuration], discriminatorSchema: c.Expr[Schema[V]]): c.Expr[Schema[E]] = {
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
    val weakTypeV = weakTypeOf[V]

    def extractTypeArguments(weakType: c.Type): List[String] = {
      def allTypeArguments(tn: c.Type): Seq[c.Type] = tn.typeArgs.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
      allTypeArguments(weakType).map(_.typeSymbol.name.decodedName.toString).toList
    }

    val name = resolveFunctionName(extractor.tree.asInstanceOf[Function])

    val schemaForE =
      q"""{
            import _root_.sttp.tapir.internal._
            import _root_.sttp.tapir.Schema
            import _root_.sttp.tapir.Schema._
            import _root_.sttp.tapir.SchemaType._
            import _root_.scala.collection.immutable.{List, Map}
            val mappingAsList = List(..$mapping)
            val mappingAsMap: Map[$weakTypeV, Schema[_]] = mappingAsList.toMap
            
            val discriminatorName = _root_.sttp.tapir.FieldName($name, $conf.toEncodedName($name))
            // cannot use .collect because of a bug in ScalaJS (Trying to access the this of another class ... during phase: jscode)
            val discriminatorMapping = mappingAsMap.toList.flatMap { 
                case (k, Schema(_, Some(fname), _, _, _, _, _, _, _, _, _)) => List($asString.apply(k) -> SRef(fname))
                case _ => Nil
              }
              .toMap
            
            val sname = SName(${weakTypeE.typeSymbol.fullName},${extractTypeArguments(weakTypeE)})
            // cast needed because of Scala 2.12
            val subtypes = mappingAsList.map(_._2)
            Schema((SCoproduct[$weakTypeE](subtypes, None) { e => 
              val ee: $weakTypeV = $extractor(e)
              mappingAsMap.get(ee).map(m => SchemaWithValue(m.asInstanceOf[Schema[Any]], e))
            }).addDiscriminatorField(
              discriminatorName, $discriminatorSchema, discriminatorMapping
            ), Some(sname))
          }"""

    Debug.logGeneratedCode(c)(weakTypeE.typeSymbol.fullName, schemaForE)
    c.Expr[Schema[E]](schemaForE)
  }
}
