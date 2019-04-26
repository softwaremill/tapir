package tapir.docs.openapi.schema

import tapir.openapi.Reference
import tapir.{Schema => TSchema, _}

class SchemaReferenceMapper(fullNameToKey: Map[TSchema.SObjectInfo, SchemaKey]) {
  def map(objectInfo: TSchema.SObjectInfo): Reference = {
    Reference("#/components/schemas/" + fullNameToKey(objectInfo))
  }
}
