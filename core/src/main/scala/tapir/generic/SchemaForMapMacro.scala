package tapir.generic

import tapir.SchemaFor

import scala.reflect.macros.blackbox

object SchemaForMapMacro {
  def mapParameters[K: c.WeakTypeTag, V: c.WeakTypeTag, M: c.WeakTypeTag](c: blackbox.Context): c.Expr[SchemaFor[M]] = {
    import c.universe._

    val k = weakTypeOf[K]
    val v = weakTypeOf[V]
    val m = weakTypeOf[M]
    val mWithKAndV = weakTypeTag[M].tpe.substituteTypes(m.typeArgs.map(_.typeSymbol), List(k, v))
    val schemaForE =
      q"""import tapir.Schema._
          new SchemaFor[$mWithKAndV]{
            def schema: Schema = SObject(SObjectInfo(${m.typeSymbol.fullName},${List(
        k.typeSymbol.name.decodedName.toString,
        v.typeSymbol.name.decodedName.toString
      )}), List.empty, List.empty)
          }
        """
    c.Expr[SchemaFor[M]](schemaForE)
  }
}
