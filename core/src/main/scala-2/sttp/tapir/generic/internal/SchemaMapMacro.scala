package sttp.tapir.generic.internal

import sttp.tapir.Schema

import scala.reflect.macros.blackbox

object SchemaMapMacro {
  /*
    Extract name and generic type parameters of map value type for object info creation
   */
  def generateSchemaForMap[V: c.WeakTypeTag](
      c: blackbox.Context
  )(schemaForV: c.Expr[Schema[V]]): c.Expr[Schema[Map[String, V]]] = {
    import c.universe._

    def extractTypeArguments(weakType: c.Type): List[String] = {
      def allTypeArguments(tn: c.Type): Seq[c.Type] = tn.typeArgs.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
      allTypeArguments(weakType).map(_.typeSymbol.name.decodedName.toString).toList
    }

    val weakTypeV = weakTypeOf[V]
    val genericTypeParametersM = List(weakTypeV.typeSymbol.name.decodedName.toString) ++ extractTypeArguments(weakTypeV)
    val schemaForMap =
      q"""{
          val s = $schemaForV
          _root_.sttp.tapir.Schema(
            _root_.sttp.tapir.SchemaType.SOpenProduct(s)(identity),
            Some(_root_.sttp.tapir.Schema.SName("Map", $genericTypeParametersM))
          )
         }"""
    Debug.logGeneratedCode(c)(weakTypeV.typeSymbol.fullName, schemaForMap)
    c.Expr[Schema[Map[String, V]]](schemaForMap)
  }
}
