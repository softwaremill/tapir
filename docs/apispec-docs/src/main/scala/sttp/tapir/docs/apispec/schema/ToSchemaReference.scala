package sttp.tapir.docs.apispec.schema

import sttp.apispec.Reference
import sttp.tapir.{Schema => TSchema}

private[schema] class ToSchemaReference(
    keyToId: Map[SchemaKey, SchemaId],
    keyToSchema: Map[SchemaKey, TSchema[_]],
    refRoot: String = "#/components/schemas/"
) {

  def map(schema: TSchema[_], name: TSchema.SName): Reference = {
    val key = SchemaKey(schema, name)
    val maybeId = keyToId.get(key)
    var result = map(key.name, maybeId)
    keyToSchema.get(key).foreach { originalSchema =>
      // checking if we don't need to enrich the reference, as it might contain additional usage-site specific properties (#1203)
      if (originalSchema.description != schema.description) result = result.copy(description = schema.description)
    }
    result
  }

  /** When mapping a reference used directly (which contains a name only), in case there are multiple schema keys with that name, we use the
    * one with a smaller number of fields - as these duplicates must be because one is a member of an inheritance hierarchy; then, we choose
    * the variant without the extra discriminator field (#2358).
    *
    * When mapping a referenced used in a discriminator, we choose the variant with the higher number of fields in [[mapDiscriminator]].
    */
  def mapDirect(name: TSchema.SName): Reference =
    map(name, keyToId.filter(_._1.name == name).toList.sortBy(_._1.fields.size).headOption.map(_._2))

  def mapDiscriminator(name: TSchema.SName): Reference =
    map(name, keyToId.filter(_._1.name == name).toList.sortBy(-_._1.fields.size).headOption.map(_._2))

  private def map(name: TSchema.SName, maybeId: Option[SchemaId]): Reference = maybeId match {
    case Some(id) => Reference.to(refRoot, id)
    case None     => Reference(name.fullName) // no reference to internal model found. assuming external reference
  }
}
