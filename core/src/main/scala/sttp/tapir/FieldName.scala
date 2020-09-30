package sttp.tapir

case class FieldName(name: String, encodedName: String)

object FieldName {
  def apply(name: String) = new FieldName(name, name)
}
