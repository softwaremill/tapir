package sttp.tapir.apispec

import scala.collection.immutable.ListMap

case class Reference($ref: String)

object Reference {
  def to(prefix: String, $ref: String): Reference = new Reference(s"$prefix${$ref}")
}

sealed trait ExampleValue
case class ExampleSingleValue(value: Any) extends ExampleValue
case class ExampleMultipleValue(values: List[Any]) extends ExampleValue

case class Tag(
    name: String,
    description: Option[String] = None,
    externalDocs: Option[ExternalDocumentation] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class ExternalDocumentation(
    url: String,
    description: Option[String] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class ExtensionValue(value: String)
