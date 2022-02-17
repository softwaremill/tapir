package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.Reference
import sttp.tapir.{Schema => TSchema}

private[schema] class NameToSchemaReference(nameToKey: Map[TSchema.SName, ObjectKey]) {
  def map(name: TSchema.SName): Reference = {
    nameToKey.get(name) match {
      case Some(key) =>
        Reference.to("#/components/schemas/", key)
      case None =>
        // no reference to internal model found. assuming external reference
        Reference(name.fullName)
    }
  }
}
