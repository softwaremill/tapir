package sttp.tapir.codegen.openapi.models
// A value that can be written as a scala literal
sealed trait RenderableValue {
  def tpe: String
  def render: String
}
// A RenderableValue that can additionally be literally lifted into the model generation code
sealed trait ReifiableRenderableValue extends RenderableValue {
  def value: Any
}
case object ReifiableValueNull extends ReifiableRenderableValue {
  val tpe = "Null"
  val render = "null"
  val value = null
}
case class ReifiableValueBoolean(value: Boolean) extends ReifiableRenderableValue {
  val render = value.toString
  val tpe = "Boolean"
}
case class ReifiableValueLong(value: Long) extends ReifiableRenderableValue {
  val render = s"${value}L"
  val tpe = "Long"
}
case class ReifiableValueDouble(value: Double) extends ReifiableRenderableValue {
  val render = s"${value}d"
  val tpe = "Double"
}
case class ReifiableValueString(value: String) extends ReifiableRenderableValue {
  val render = '"' +: value :+ '"'
  val tpe = "String"
}
case class ReifiableValueList(values: Seq[ReifiableRenderableValue]) extends ReifiableRenderableValue {
  val render = s"Vector(${values.map(_.render).mkString(", ")})"
  def tpe = values.map(_.tpe).distinct match { case single +: Nil => s"Seq[$single]"; case _ => "Seq[Any]" }
  def value = values.map(_.value)
}
case class ReifiableValueMap(kvs: Map[String, ReifiableRenderableValue]) extends ReifiableRenderableValue {
  val render = s"Map(${kvs.map { case (k, v) => s""""$k" -> ${v.render}""" }.mkString(", ")})"
  def tpe = kvs.values.map(_.tpe).toSeq.distinct match { case single +: Nil => s"Map[String, $single]"; case _ => "Map[String, Any]" }
  def value = kvs.map { case (k, v) => k -> v.value }
}
case class RenderableClassModel(name: String, kvs: Map[String, RenderableValue]) extends RenderableValue {
  val render = s"$name(${kvs.map { case (k, v) => s"""$k = ${v.render}""" }.mkString(", ")})"
  def tpe = name
}
