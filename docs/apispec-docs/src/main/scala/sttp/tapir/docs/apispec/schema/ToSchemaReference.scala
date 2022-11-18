package sttp.tapir.docs.apispec.schema

import sttp.apispec.Reference
import sttp.tapir.{Schema => TSchema}

private[schema] class ToSchemaReference(keyToId: Map[SchemaKey, SchemaId]) {

  def map(key: SchemaKey): Reference = map(key.name, keyToId.get(key))

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
    case Some(id) =>
      Reference.to("#/components/schemas/", id)
    case None =>
      // no reference to internal model found. assuming external reference
      Reference(name.fullName)
  }
}
