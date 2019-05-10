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
      case TSchema.SArray(o: TSchema.SObject) =>
        Right(
          OSchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(o.info))))
        )
      case o: TSchema.SObject => Left(schemaReferenceMapper.map(o.info))
      case _                  => tschemaToOSchema(schema)
    }
  }
}
