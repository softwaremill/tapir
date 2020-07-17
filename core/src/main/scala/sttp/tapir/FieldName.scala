package sttp.tapir

case class FieldName(name: String, lowLevelName: String)

object FieldName {
  def apply(name: String) = new FieldName(name, name)
}
