package sttp.tapir.codegen.openapi.models

import io.circe.Json
import sttp.tapir.codegen.openapi.models.OpenapiSchemaType.{
  OpenapiSchemaArray,
  OpenapiSchemaBinary,
  OpenapiSchemaBoolean,
  OpenapiSchemaDateTime,
  OpenapiSchemaDouble,
  OpenapiSchemaEnum,
  OpenapiSchemaFloat,
  OpenapiSchemaInt,
  OpenapiSchemaLong,
  OpenapiSchemaMap,
  OpenapiSchemaObject,
  OpenapiSchemaRef,
  OpenapiSchemaString,
  OpenapiSchemaUUID
}

case class RenderConfig(maybeEnumName: Option[String] = None)

object DefaultValueRenderer {
  private def lookup(allModels: Map[String, OpenapiSchemaType], ref: OpenapiSchemaRef): OpenapiSchemaType = allModels(
    ref.name.stripPrefix("#/components/schemas/")
  )

  private def renderStringWithName(
      value: String
  )(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, name: String): String =
    thisType match {
      case ref: OpenapiSchemaRef =>
        renderStringWithName(value)(allModels, lookup(allModels, ref), ref.name.stripPrefix("#/components/schemas/"))
      case OpenapiSchemaString(_, _, _, _) => '"' +: value :+ '"'
      case OpenapiSchemaEnum(_, _, _)      => s"$name.$value"
      case OpenapiSchemaDateTime(_)        => s"""java.time.Instant.parse("$value")"""
      case OpenapiSchemaBinary(_)          => s""""$value".getBytes("utf-8")"""
      case OpenapiSchemaUUID(_)            => s"""java.util.UUID.fromString("$value")"""
      case other => throw new IllegalArgumentException(s"Cannot render ${value} as type ${other.getClass.getName}")
    }
  private def renderMapWithName(
      kvs: Map[String, Json]
  )(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, name: String): String = {
    def errorForKey(k: String): Nothing = throw new IllegalArgumentException(
      s"Cannot find property $k in schema $name when constructing default value"
    )
    thisType match {
      case ref: OpenapiSchemaRef => renderMapWithName(kvs)(allModels, lookup(allModels, ref), ref.name.stripPrefix("#/components/schemas/"))
      case OpenapiSchemaMap(types, _, _) =>
        s"Map(${kvs.map { case (k, v) => s""""$k" -> ${render(allModels, types, isOptional = false, RenderConfig())(v)}""" }.mkString(", ")})"
      case OpenapiSchemaObject(properties, required, _, _) =>
        val kvsWithProps = kvs.map { case (k, v) => (k, (v, properties.get(k).getOrElse(errorForKey(k)))) }
        s"$name(${kvsWithProps
            .map { case (k, (v, p)) =>
              s"""$k = ${render(allModels, p.`type`, p.`type`.nullable || !required.contains(k), RenderConfig())(v)}"""
            }
            .mkString(", ")})"
      case other => throw new IllegalArgumentException(s"Cannot render a map as type ${other.getClass.getName}")
    }
  }

  def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean, config: RenderConfig)(
      json: Json
  ): String =
    if (json == Json.Null) {
      if (isOptional) "None" else "null"
    } else {
      def fail(tpe: String, schemaType: OpenapiSchemaType, reason: Option[String] = None): Nothing =
        throw new IllegalArgumentException(
          s"Cannot render a $tpe as type ${schemaType.getClass.getName}.${reason.map(" " + _).getOrElse("")}"
        )
      val base: String = json.fold[String](
        "null",
        jsBool =>
          thisType match {
            case ref: OpenapiSchemaRef   => render(allModels, lookup(allModels, ref), isOptional = false, config)(json)
            case OpenapiSchemaBoolean(_) => jsBool.toString
            case other                   => fail("boolean", other)
          },
        jsonNumber =>
          thisType match {
            case ref: OpenapiSchemaRef       => render(allModels, lookup(allModels, ref), isOptional = false, config)(json)
            case l @ OpenapiSchemaLong(_, _) => s"${jsonNumber.toLong.getOrElse(fail("number", l, Some(s"$jsonNumber is not a long")))}L"
            case i @ OpenapiSchemaInt(_, _)  => jsonNumber.toInt.getOrElse(fail("number", i, Some(s"$jsonNumber is not an int"))).toString
            case OpenapiSchemaFloat(_, _)    => s"${jsonNumber.toFloat}f"
            case OpenapiSchemaDouble(_, _)   => s"${jsonNumber.toDouble}d"
            case other                       => fail("number", other)
          },
        jsonString =>
          thisType match {
            case ref: OpenapiSchemaRef =>
              renderStringWithName(jsonString)(allModels, lookup(allModels, ref), ref.name.stripPrefix("#/components/schemas/"))
            case OpenapiSchemaString(_, _, _, _) => '"' +: jsonString :+ '"'
            case OpenapiSchemaDateTime(_)        => s"""java.time.Instant.parse("$jsonString")"""
            case OpenapiSchemaBinary(_)          => s""""$jsonString".getBytes("utf-8")"""
            case OpenapiSchemaUUID(_)            => s"""java.util.UUID.fromString("$jsonString")"""
            case OpenapiSchemaEnum(_, _, _) if config.maybeEnumName.isDefined =>
              s"${config.maybeEnumName.get}.$jsonString"
            //      case OpenapiSchemaEnum(_, _, _) => // inline enum definitions are not currently supported, so let it throw
            case other => fail("string", other)
          },
        jsonArray =>
          thisType match {
            case ref: OpenapiSchemaRef => render(allModels, lookup(allModels, ref), isOptional = false, config)(json)
            case OpenapiSchemaArray(items, _, _, rs) =>
              val container = if (rs.uniqueItems.contains(true)) "Set" else "Vector"
              s"$container(${jsonArray.map(render(allModels, items, isOptional = false, config)).mkString(", ")})"
            case other => fail("list", other)
          },
        jsonObject =>
          thisType match {
            case ref: OpenapiSchemaRef =>
              renderMapWithName(jsonObject.toMap)(allModels, lookup(allModels, ref), ref.name.stripPrefix("#/components/schemas/"))
            case OpenapiSchemaMap(types, _, _) =>
              s"Map(${jsonObject.toMap.map { case (k, v) => s""""$k" -> ${render(allModels, types, isOptional = false, config)(v)}""" }.mkString(", ")})"
            case other => fail("map", other)
          }
      )
      if (isOptional) s"Some($base)" else base
    }

}
