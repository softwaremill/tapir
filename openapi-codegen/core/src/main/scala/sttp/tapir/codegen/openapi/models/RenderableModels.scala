package sttp.tapir.codegen.openapi.models

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

// A value that can be written as a scala literal
sealed trait RenderableValue {
  def tpe: String
  def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String
  def lookup(allModels: Map[String, OpenapiSchemaType], ref: OpenapiSchemaRef): OpenapiSchemaType = allModels(
    ref.name.stripPrefix("#/components/schemas/")
  )
}
// A RenderableValue that can additionally be literally lifted into the model generation code
sealed trait ReifiableRenderableValue extends RenderableValue {
  def value: Any
}
case object ReifiableValueNull extends ReifiableRenderableValue {
  val tpe = "Null"
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String =
    if (isOptional) "None" else "null"
  val value = null
}
case class ReifiableValueBoolean(value: Boolean) extends ReifiableRenderableValue {
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String = {
    val base = thisType match {
      case ref: OpenapiSchemaRef   => render(allModels, lookup(allModels, ref), isOptional = false)
      case OpenapiSchemaBoolean(_) => value.toString
      case other                   => throw new IllegalArgumentException(s"Cannot render a boolean for type ${other.getClass.getName}")
    }
    if (isOptional) s"Some($base)" else base
  }
  val tpe = "Boolean"
}
case class ReifiableValueLong(value: Long) extends ReifiableRenderableValue {
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String = {
    val base = thisType match {
      case ref: OpenapiSchemaRef  => render(allModels, lookup(allModels, ref), isOptional = false)
      case OpenapiSchemaLong(_)   => s"${value}L"
      case OpenapiSchemaInt(_)    => value.toString
      case OpenapiSchemaFloat(_)  => s"${value.toFloat}f"
      case OpenapiSchemaDouble(_) => s"${value.toDouble}d"
      case other                  => throw new IllegalArgumentException(s"Cannot render a long for type ${other.getClass.getName}")
    }
    if (isOptional) s"Some($base)" else base
  }
  val tpe = "Long"
}
case class ReifiableValueDouble(value: Double) extends ReifiableRenderableValue {
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String = {
    val base = thisType match {
      case ref: OpenapiSchemaRef  => render(allModels, lookup(allModels, ref), isOptional = false)
      case OpenapiSchemaFloat(_)  => s"${value.toFloat}f"
      case OpenapiSchemaDouble(_) => s"${value}d"
      case other                  => throw new IllegalArgumentException(s"Cannot render a double for type ${other.getClass.getName}")
    }
    if (isOptional) s"Some($base)" else base
  }
  val tpe = "Double"
}
case class ReifiableValueString(value: String) extends ReifiableRenderableValue {
  private def renderWithName(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, name: String): String =
    thisType match {
      case ref: OpenapiSchemaRef      => render(allModels, lookup(allModels, ref), isOptional = false)
      case OpenapiSchemaString(_)     => '"' +: value :+ '"'
      case OpenapiSchemaEnum(_, _, _) => s"$name.$value"
      case OpenapiSchemaDateTime(_)   => s"""java.time.Instant.parse("$value")"""
      case OpenapiSchemaBinary(_)     => s""""$value".getBytes("utf-8")"""
      case OpenapiSchemaUUID(_)       => s"""java.util.UUID.fromString("$value")"""
      case other                      => throw new IllegalArgumentException(s"Cannot render a string for type ${other.getClass.getName}")
    }
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String = {
    val base = thisType match {
      case ref: OpenapiSchemaRef        => renderWithName(allModels, lookup(allModels, ref), ref.name.stripPrefix("#/components/schemas/"))
      case OpenapiSchemaString(_)       => '"' +: value :+ '"'
      case OpenapiSchemaEnum(tpe, _, _) => s"$value"
      case OpenapiSchemaDateTime(_)     => s"""java.time.Instant.parse("$value")"""
      case OpenapiSchemaBinary(_)       => s""""$value".getBytes("utf-8")"""
      case OpenapiSchemaUUID(_)         => s"""java.util.UUID.fromString("$value")"""
      case other                        => throw new IllegalArgumentException(s"Cannot render a string for type ${other.getClass.getName}")
    }
    if (isOptional) s"Some($base)" else base
  }
  val tpe = "String"
}
case class ReifiableValueList(values: Seq[ReifiableRenderableValue]) extends ReifiableRenderableValue {
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String = {
    val base = thisType match {
      case ref: OpenapiSchemaRef        => render(allModels, lookup(allModels, ref), isOptional = false)
      case OpenapiSchemaArray(items, _) => s"Vector(${values.map(_.render(allModels, items, isOptional = false)).mkString(", ")})"
      case other                        => throw new IllegalArgumentException(s"Cannot render a list for type ${other.getClass.getName}")
    }
    if (isOptional) s"Some($base)" else base
  }

  def tpe = values.map(_.tpe).distinct match { case single +: Nil => s"Seq[$single]"; case _ => "Seq[Any]" }
  def value = values.map(_.value)
}
case class ReifiableValueMap(kvs: Map[String, ReifiableRenderableValue]) extends ReifiableRenderableValue {
  private def renderWithName(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, name: String): String = {
    def errorForKey(k: String): Nothing = throw new IllegalArgumentException(
      s"Cannot find property $k in schema $name when constructing default value"
    )
    thisType match {
      case ref: OpenapiSchemaRef => renderWithName(allModels, lookup(allModels, ref), ref.name.stripPrefix("#/components/schemas/"))
      case OpenapiSchemaMap(types, _) =>
        s"Map(${kvs.map { case (k, v) => s""""$k" -> ${v.render(allModels, types, isOptional = false)}""" }.mkString(", ")})"
      case OpenapiSchemaObject(properties, required, _) =>
        val kvsWithProps = kvs.map { case (k, v) => (k, (v, properties.get(k).getOrElse(errorForKey(k)))) }
        s"$name(${kvsWithProps
            .map { case (k, (v, p)) => s"""$k = ${v.render(allModels, p.`type`, p.`type`.nullable || !required.contains(k))}""" }
            .mkString(", ")})"
      case other => throw new IllegalArgumentException(s"Cannot render a list for type ${other.getClass.getName}")
    }
  }
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String = {
    val base = thisType match {
      case ref: OpenapiSchemaRef => renderWithName(allModels, lookup(allModels, ref), ref.name.stripPrefix("#/components/schemas/"))
      case OpenapiSchemaMap(types, _) =>
        s"Map(${kvs.map { case (k, v) => s""""$k" -> ${v.render(allModels, types, isOptional = false)}""" }.mkString(", ")})"
      case other => throw new IllegalArgumentException(s"Cannot render a list for type ${other.getClass.getName}")
    }
    if (isOptional) s"Some($base)" else base
  }
  def tpe = kvs.values.map(_.tpe).toSeq.distinct match { case single +: Nil => s"Map[String, $single]"; case _ => "Map[String, Any]" }
  def value = kvs.map { case (k, v) => k -> v.value }
}
case class RenderableClassModel(name: String, kvs: Map[String, RenderableValue]) extends RenderableValue {
  private def errorForKey(k: String): Nothing = throw new IllegalArgumentException(
    s"Cannot find property $k in schema $name when constructing default value"
  )
  override def render(allModels: Map[String, OpenapiSchemaType], thisType: OpenapiSchemaType, isOptional: Boolean): String = {
    val base = thisType match {
      case ref: OpenapiSchemaRef if !ref.name.endsWith(s"/$name") =>
        throw new IllegalArgumentException(s"Cannot render $name as ${ref.name.split('/').last}")
      case ref: OpenapiSchemaRef => render(allModels, lookup(allModels, ref), isOptional = false)
      case OpenapiSchemaMap(types, _) =>
        s"$name(${kvs.map { case (k, v) => s"""$k = ${v.render(allModels, types, isOptional = false)}""" }.mkString(", ")})"
      case OpenapiSchemaObject(properties, required, _) =>
        val kvsWithProps = kvs.map { case (k, v) => (k, (v, properties.getOrElse(k, errorForKey(k)))) }
        s"$name(${kvsWithProps
            .map { case (k, (v, p)) => s"""$k = ${v.render(allModels, p.`type`, p.`type`.nullable || !required.contains(k))}""" }
            .mkString(", ")})"
      case other => throw new IllegalArgumentException(s"Cannot render a list for type ${other.getClass.getName}")
    }
    if (isOptional) s"Some($base)" else base
  }
  def tpe = name
}
