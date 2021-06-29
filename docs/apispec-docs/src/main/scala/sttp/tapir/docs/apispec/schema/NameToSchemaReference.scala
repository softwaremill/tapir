package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.Reference
import sttp.tapir.{Schema => TSchema}

private[schema] class NameToSchemaReference(nameToKey: Map[TSchema.SName, ObjectKey]) {
  def map(name: TSchema.SName): Reference = {
    Reference.to("#/components/schemas/", nameToKey(name))
  }
}
