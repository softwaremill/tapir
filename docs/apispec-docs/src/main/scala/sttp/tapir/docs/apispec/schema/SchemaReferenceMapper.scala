package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.Reference
import sttp.tapir.{SchemaType => TSchemaType}

class SchemaReferenceMapper(fullNameToKey: Map[TSchemaType.SObjectInfo, SchemaKey]) {
  def map(objectInfo: TSchemaType.SObjectInfo): Reference = {
    Reference(fullNameToKey(objectInfo))
  }
}
