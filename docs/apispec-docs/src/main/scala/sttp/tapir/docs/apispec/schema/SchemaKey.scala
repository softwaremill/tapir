package sttp.tapir.docs.apispec.schema

import sttp.tapir.{SchemaType, Schema => TSchema}

/** A schema key consists of both the name, and the schema's fields, in case this is a product. This is needed as the same schema name can
  * have two different sets of fields, in case the class is a member of an inheritance hierarchy, and a discriminator field is used (#2358).
  */
private[docs] case class SchemaKey(name: TSchema.SName, fields: Set[String])

private[docs] object SchemaKey {
  def apply(schema: TSchema[_]): Option[SchemaKey] = schema.name.map(apply(schema, _))

  def apply(schema: TSchema[_], name: TSchema.SName): SchemaKey = {
    val fields = schema.schemaType match {
      case SchemaType.SProduct(fields)        => fields.map(_.name.name).toSet
      case SchemaType.SOpenProduct(fields, _) => fields.map(_.name.name).toSet
      case _                                  => Set.empty[String]
    }

    SchemaKey(name, fields)
  }
}
