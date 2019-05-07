package tapir.docs.openapi.schema

import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{SchemaType, Schema => OSchema}
import tapir.{Schema => TSchema}

class ObjectSchemas(
    tschemaToOSchema: TSchemaToOSchema,
    schemaReferenceMapper: SchemaReferenceMapper
) {
  def apply(schema: TSchema): ReferenceOr[OSchema] = {
    schema match {
      case TSchema.SObject(info, _, _) => Left(schemaReferenceMapper.map(info))
      case TSchema.SArray(objectable: TSchema.SObjectable) =>
        Right(
          OSchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(objectable.info))))
        )
      case TSchema.SCoproduct(info, _, _) => Left(schemaReferenceMapper.map(info))
      case _                              => tschemaToOSchema(schema)
    }
  }
}
