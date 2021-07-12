package sttp.tapir.apispec

import scala.collection.immutable.ListMap

// todo: discriminator, xml, json-schema properties
case class Schema(
    allOf: List[ReferenceOr[Schema]] = List.empty,
    title: Option[String] = None,
    required: List[String] = List.empty,
    `type`: Option[SchemaType] = None,
    items: Option[ReferenceOr[Schema]] = None,
    properties: ListMap[String, ReferenceOr[Schema]] = ListMap.empty,
    description: Option[String] = None,
    format: Option[String] = None,
    default: Option[ExampleValue] = None,
    nullable: Option[Boolean] = None,
    readOnly: Option[Boolean] = None,
    writeOnly: Option[Boolean] = None,
    example: Option[ExampleValue] = None,
    deprecated: Option[Boolean] = None,
    oneOf: List[ReferenceOr[Schema]] = List.empty,
    discriminator: Option[Discriminator] = None,
    additionalProperties: Option[ReferenceOr[Schema]] = None,
    pattern: Option[String] = None,
    minLength: Option[Int] = None,
    maxLength: Option[Int] = None,
    minimum: Option[BigDecimal] = None,
    exclusiveMinimum: Option[Boolean] = None,
    maximum: Option[BigDecimal] = None,
    exclusiveMaximum: Option[Boolean] = None,
    minItems: Option[Int] = None,
    maxItems: Option[Int] = None,
    `enum`: Option[List[ExampleSingleValue]] = None,
    extensions: ListMap[String, ExtensionValue] = ListMap.empty
)

case class Discriminator(propertyName: String, mapping: Option[ListMap[String, String]])

object Schema {
  def apply(`type`: SchemaType): Schema = new Schema(`type` = Some(`type`))

  def apply(references: List[ReferenceOr[Schema]], discriminator: Option[Discriminator]): Schema =
    new Schema(oneOf = references, discriminator = discriminator)
}

sealed abstract class SchemaType(val value: String)
object SchemaType {
  case object Boolean extends SchemaType("boolean")
  case object Object extends SchemaType("object")
  case object Array extends SchemaType("array")
  case object Number extends SchemaType("number")
  case object String extends SchemaType("string")
  case object Integer extends SchemaType("integer")
}

object SchemaFormat {
  val Int32: Option[String] = Some("int32")
  val Int64: Option[String] = Some("int64")
  val Float: Option[String] = Some("float")
  val Double: Option[String] = Some("double")
  val Byte: Option[String] = Some("byte")
  val Binary: Option[String] = Some("binary")
  val Date: Option[String] = Some("date")
  val DateTime: Option[String] = Some("date-time")
  val Password: Option[String] = Some("password")
}
