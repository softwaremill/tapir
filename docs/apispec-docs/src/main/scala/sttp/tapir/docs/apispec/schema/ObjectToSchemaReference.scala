package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.Reference
import sttp.tapir.{SchemaType => TSchemaType}

private[schema] class ObjectToSchemaReference(infoToKey: Map[TSchemaType.SObjectInfo, ObjectKey]) {
  def map(objectInfo: TSchemaType.SObjectInfo): Reference = {
    Reference.to("#/components/schemas/", infoToKey(objectInfo))
  }
}
