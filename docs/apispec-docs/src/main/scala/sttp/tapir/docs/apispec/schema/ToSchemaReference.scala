package sttp.tapir.docs.apispec.schema

import sttp.apispec.{Schema => ASchema}
import sttp.tapir.{Schema => TSchema}
import sttp.tapir.Schema.{Title, Nullable}
import sttp.tapir.docs.apispec.schema.TSchemaToASchema.{tDefaultToADefault, tExampleToAExample}

private[schema] class ToSchemaReference(
    keyToId: Map[SchemaKey, SchemaId],
    keyToSchema: Map[SchemaKey, TSchema[_]],
    refRoot: String = "#/components/schemas/"
) {
  def map(schema: TSchema[_], name: TSchema.SName): ASchema = {
    val key = SchemaKey(schema, name)
    val maybeId = keyToId.get(key)
    var result = map(key.name, maybeId)
    keyToSchema.get(key).foreach { originalSchema =>
      // checking if we don't need to enrich the reference, as it might contain additional usage-site specific properties
      // the differently customised properties are reset to their default values in ToKeyedSchemas (#1203)
      if (originalSchema.description != schema.description) result = result.copy(description = schema.description)
      if (originalSchema.default != schema.default) result = result.copy(default = tDefaultToADefault(schema))
      if (originalSchema.encodedExample != schema.encodedExample) result = result.copy(examples = tExampleToAExample(schema).map(List(_)))
      if (originalSchema.deprecated != schema.deprecated && schema.deprecated) result = result.copy(deprecated = Some(schema.deprecated))
      if (originalSchema.attributes.get(Title.Attribute) != schema.attributes.get(Title.Attribute))
        result = result.copy(title = schema.attributes.get(Title.Attribute).map(_.value))
      if (
        originalSchema.attributes.get(Nullable.Attribute).exists(_.nullable) || schema.attributes.get(Nullable.Attribute).exists(_.nullable)
      )
        result = result.nullable
    }
    result
  }

  /** When mapping a reference used directly (which contains a name only), in case there are multiple schema keys with that name, we use the
    * one with a smaller number of fields - as these duplicates must be because one is a member of an inheritance hierarchy; then, we choose
    * the variant without the extra discriminator field (#2358).
    *
    * When mapping a referenced used in a discriminator, we choose the variant with the higher number of fields in [[mapDiscriminator]].
    */
  def mapDirect(name: TSchema.SName): ASchema =
    map(name, keyToId.filter(_._1.name == name).toList.sortBy(_._1.fields.size).headOption.map(_._2))

  def mapDiscriminator(name: TSchema.SName): ASchema =
    map(name, keyToId.filter(_._1.name == name).toList.sortBy(-_._1.fields.size).headOption.map(_._2))

  private def map(name: TSchema.SName, maybeId: Option[SchemaId]): ASchema = maybeId match {
    case Some(id) => ASchema.referenceTo(refRoot, id)
    case None     => ASchema.referenceTo("", name.fullName) // no reference to internal model found. assuming external reference
  }
}
