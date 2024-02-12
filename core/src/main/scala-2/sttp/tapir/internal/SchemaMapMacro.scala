package sttp.tapir.internal

import sttp.tapir.Schema

import scala.reflect.macros.blackbox

private[tapir] object SchemaMapMacro {
  def generateSchemaForStringMap[V: c.WeakTypeTag](
      c: blackbox.Context
  )(schemaForV: c.Expr[Schema[V]]): c.Expr[Schema[Map[String, V]]] = {
    import c.universe._
    generateSchemaForMap[String, V](c)(c.Expr[String => String](q"""_root_.scala.Predef.identity"""))(schemaForV)
  }

  /* Extract name and generic type parameters of map value type for object info creation */
  def generateSchemaForMap[K: c.WeakTypeTag, V: c.WeakTypeTag](
      c: blackbox.Context
  )(keyToString: c.Expr[K => String])(schemaForV: c.Expr[Schema[V]]): c.Expr[Schema[Map[K, V]]] = {
    import c.universe._

    def extractTypeArguments(weakType: c.Type): List[String] = {
      def allTypeArguments(tn: c.Type): Seq[c.Type] = tn.typeArgs.flatMap(tn2 => tn2 +: allTypeArguments(tn2))
      allTypeArguments(weakType).map(_.typeSymbol.fullName).toList
    }

    val weakTypeV = weakTypeOf[V]
    val weakTypeK = weakTypeOf[K]

    val keyTypeParameter = weakTypeK.typeSymbol.fullName

    val genericTypeParameters =
      (if (keyTypeParameter.split('.').lastOption.contains("String")) Nil else List(keyTypeParameter)) ++
        extractTypeArguments(weakTypeK) ++ List(weakTypeV.typeSymbol.fullName) ++ extractTypeArguments(weakTypeV)
    val schemaForMap =
      q"""{
          val s = $schemaForV
          _root_.sttp.tapir.Schema(
            _root_.sttp.tapir.SchemaType.SOpenProduct(_root_.scala.Nil, s)(_.map { case (k, v) => ($keyToString(k), v) }),
            _root_.scala.Some(_root_.sttp.tapir.Schema.SName("Map", $genericTypeParameters))
          )
         }"""
    Debug.logGeneratedCode(c)(weakTypeV.typeSymbol.fullName, schemaForMap)
    c.Expr[Schema[Map[K, V]]](schemaForMap)
  }
}
