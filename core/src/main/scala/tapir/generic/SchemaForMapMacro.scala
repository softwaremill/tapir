package tapir.generic

import tapir.SchemaFor

import scala.reflect.macros.blackbox

object SchemaForMapMacro {
  /*
    Extract name and generic type parameters of map value type for object info creation
   */
  def schemaForMap[M: c.WeakTypeTag, V: c.WeakTypeTag](
      c: blackbox.Context
  )(schemaForV: c.Expr[SchemaFor[V]]): c.Expr[SchemaFor[Map[String, V]]] = {
    import c.universe._

    def extractTypeArguments(weakType: c.Type): List[String] = {
      def allTypeArguments(tn: c.Type): Seq[c.Type] = tn.typeArgs.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
      allTypeArguments(weakType).map(_.typeSymbol.name.decodedName.toString).toList
    }

    val weakTypeV = weakTypeOf[V]
    val genericTypeParametersM = List(weakTypeV.typeSymbol.name.decodedName.toString) ++ extractTypeArguments(weakTypeV)
    val schemaForMap =
      q"""tapir.SchemaFor(tapir.Schema.SOpenProduct(tapir.Schema.SObjectInfo("Map", ${genericTypeParametersM}), ${schemaForV}.schema))"""
    Debug.logGeneratedCode(c)(weakTypeV.typeSymbol.fullName, schemaForMap)
    c.Expr[SchemaFor[Map[String, V]]](schemaForMap)
  }
}
