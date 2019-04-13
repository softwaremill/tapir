package tapir.docs.openapi.schema

import tapir.Schema.SCoproduct
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema, _}
import tapir.{Schema => TSchema}

/**
  * Converts a tapir schema to an OpenAPI schema, using the given map to resolve references.
  */
private[schema] class TSchemaToOSchema(schemaReferenceMapper: SchemaReferenceMapper, discriminatorToOpenApi: DiscriminatorToOpenApi) {
  def apply(schema: TSchema): ReferenceOr[OSchema] = {
    schema match {
      case TSchema.SInteger =>
        Right(OSchema(SchemaType.Integer))
      case TSchema.SNumber =>
        Right(OSchema(SchemaType.Number))
      case TSchema.SBoolean =>
        Right(OSchema(SchemaType.Boolean))
      case TSchema.SString =>
        Right(OSchema(SchemaType.String))
      case TSchema.SObject(_, fields, required) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = required.toList,
            properties = fields.map {
              case (fieldName, fieldSchema) =>
                fieldName -> apply(fieldSchema)
            }.toListMap
          ))
      case TSchema.SArray(el) =>
        Right(
          OSchema(SchemaType.Array).copy(
            items = Some(apply(el))
          ))
      case TSchema.SBinary =>
        Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Binary)))
      case TSchema.SDate =>
        Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Date)))
      case TSchema.SDateTime =>
        Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.DateTime)))
      case TSchema.SRef(fullName) =>
        Left(schemaReferenceMapper.map(fullName))
      case SCoproduct(schemas, d) =>
        Right(
          OSchema.apply(schemas.collect { case s: TSchema.SObject => Left(schemaReferenceMapper.map(s.info.fullName)) },
                        d.map(discriminatorToOpenApi.apply)))
    }
  }
}
