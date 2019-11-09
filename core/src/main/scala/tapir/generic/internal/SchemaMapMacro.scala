package tapir.generic.internal

import tapir.Schema

import scala.reflect.macros.blackbox

object SchemaMapMacro {
  /*
    Extract name and generic type parameters of map value type for object info creation
   */
  def schemaForMap[M: c.WeakTypeTag, V: c.WeakTypeTag](
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
      q"""tapir.Schema(tapir.SchemaType.SOpenProduct(tapir.SchemaType.SObjectInfo("Map", ${genericTypeParametersM}), ${schemaForV}))"""
    Debug.logGeneratedCode(c)(weakTypeV.typeSymbol.fullName, schemaForMap)
    c.Expr[Schema[Map[String, V]]](schemaForMap)
  }
}
