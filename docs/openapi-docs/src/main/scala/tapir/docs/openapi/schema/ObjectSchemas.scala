package tapir.docs.openapi.schema

import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema}
import tapir.{Schema => TSchema}

class ObjectSchemas(
    tschemaToOSchema: TSchemaToOSchema,
    schemaReferenceMapper: SchemaReferenceMapper,
    discriminatorToOpenApi: DiscriminatorToOpenApi
) {
  def apply(schema: TSchema): ReferenceOr[OSchema] = {
    schema match {
      case TSchema.SObject(info, _, _) => Left(schemaReferenceMapper.map(info))
      case TSchema.SCoproduct(schemas, d) =>
        Right(OSchema.apply(schemas.map(apply).toList, d.map(discriminatorToOpenApi.apply)))
      case _ => tschemaToOSchema(schema)
    }
  }
}
