package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.Reference
import sttp.tapir.{Schema => TSchema}

private[schema] class NameToSchemaReference(nameToKey: Map[TSchema.SName, ObjectKey]) {
  def map(name: TSchema.SName): Reference = {
    nameToKey.get(name).fold(Reference.to(name.fullName, ""))(key => Reference.to("#/components/schemas/", key))
  }
}
