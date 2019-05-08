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
      case TSchema.SArray(objectable: TSchema.SObjectable) =>
        Right(
          OSchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(objectable.info))))
        )
      case s: TSchema.SObjectable => Left(schemaReferenceMapper.map(s.info))
      case _                      => tschemaToOSchema(schema)
    }
  }
}
