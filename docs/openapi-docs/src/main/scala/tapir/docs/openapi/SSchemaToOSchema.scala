package tapir.docs.openapi

import tapir.Schema.SRef
import tapir.docs.openapi.ObjectSchemasForEndpoints.SchemaKey
import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{Schema => OSchema, _}
import tapir.{Schema => SSchema}

class SSchemaToOSchema(fullNameToKey: Map[String, SchemaKey]) {
  def apply(schema: SSchema): ReferenceOr[OSchema] = {
    schema match {
      case SSchema.SInteger =>
        Right(OSchema(SchemaType.Integer))
      case SSchema.SNumber =>
        Right(OSchema(SchemaType.Number))
      case SSchema.SBoolean =>
        Right(OSchema(SchemaType.Boolean))
      case SSchema.SString =>
        Right(OSchema(SchemaType.String))
      case SSchema.SObject(_, fields, required) =>
        Right(
          OSchema(SchemaType.Object).copy(
            required = Some(required.toList),
            properties = Some(
              fields.map {
                case (fieldName, fieldSchema) =>
                  fieldName -> apply(fieldSchema)
              }.toMap
            )
          ))
      case SSchema.SArray(el) =>
        Right(
          OSchema(SchemaType.Array).copy(
            items = Some(apply(el))
          ))
      case SSchema.SBinary() =>
        Right(OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Binary)))
      case SRef(fullName) =>
        Left(Reference("#/components/schemas/" + fullNameToKey(fullName)))
    }
  }
}
