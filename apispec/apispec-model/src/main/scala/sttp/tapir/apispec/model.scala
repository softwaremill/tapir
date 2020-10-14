package sttp.tapir.apispec

case class Reference($ref: String) {
  def dereference: String = $ref.replace(Reference.ReferencePrefix, "")
}

object Reference {
  private val ReferencePrefix = "#/components/schemas/"
  def apply($ref: String): Reference = new Reference(s"$ReferencePrefix${$ref}")
}

sealed trait ExampleValue

case class ExampleSingleValue(value: String) extends ExampleValue
case class ExampleMultipleValue(values: List[String]) extends ExampleValue

case class Tag(name: String, description: Option[String] = None, externalDocs: Option[ExternalDocumentation] = None)

case class ExternalDocumentation(url: String, description: Option[String] = None)
