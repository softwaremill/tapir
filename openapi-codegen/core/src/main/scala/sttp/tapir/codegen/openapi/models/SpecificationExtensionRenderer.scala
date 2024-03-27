package sttp.tapir.codegen.openapi.models

import io.circe.Json

object SpecificationExtensionRenderer {

  def renderCombinedType(jsons: Seq[Json]): String = {
    // permit nulls for any type, but specify type as null if every value is null
    val nonNull = jsons.filterNot(_.isNull)
    if (jsons.isEmpty) "Nothing"
    else if (nonNull.isEmpty) "Null"
    else {
      val groupedByBaseType = nonNull.groupBy(j =>
        if (j.isBoolean) "Boolean"
        else if (j.isNumber) "Number"
        else if (j.isString) "String"
        else if (j.isArray) "Array"
        else if (j.isObject) "Object"
        else throw new IllegalStateException("json must be one of boolean, number, string, array or object")
      )
      // Cannot resolve types if totally different...
      if (groupedByBaseType.size > 1) "Any"
      else
        groupedByBaseType.head match {
          case (t @ ("Boolean" | "String"), _) => t
          case ("Number", vs)                  => if (vs.forall(_.asNumber.flatMap(_.toLong).isDefined)) "Long" else "Double"
          case ("Array", vs) =>
            val t = renderCombinedType(vs.flatMap(_.asArray).flatten)
            s"Seq[$t]"
          case ("Object", kvs) =>
            val t = renderCombinedType(kvs.flatMap(_.asObject).flatMap(_.toMap.values))
            s"Map[String, $t]"
          case (x, _) => throw new IllegalStateException(s"No such group $x")
        }
    }
  }

  def renderValue(json: Json): String = json.fold(
    "null",
    bool => bool.toString,
    n => n.toLong.map(l => s"${l}L") getOrElse s"${n.toDouble}d", // the long repr is fine even if type expanded to Double
    s => '"' +: s :+ '"',
    arr => if (arr.isEmpty) "Vector.empty" else s"Vector(${arr.map(renderValue).mkString(", ")})",
    obj =>
      if (obj.isEmpty) "Map.empty[String, Nothing]"
      else s"Map(${obj.toMap.map { case (k, v) => s""""$k" -> ${renderValue(v)}""" }.mkString(", ")})"
  )
}
