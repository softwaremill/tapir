package tapir.docs.openapi.schema

import tapir.openapi.Reference
import tapir.{SchemaType => TSchemaType}

class SchemaReferenceMapper(fullNameToKey: Map[TSchemaType.SObjectInfo, SchemaKey]) {
  def map(objectInfo: TSchemaType.SObjectInfo): Reference = {
    Reference("#/components/schemas/" + fullNameToKey(objectInfo))
  }
}
