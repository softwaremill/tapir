package tapir.docs.openapi

import tapir.openapi.{Schema => OSchema, _}
import tapir.{Schema => SSchema}

object SSchemaToOSchema {
  def apply(schema: SSchema): OSchema = {
    schema match {
      case SSchema.SInteger =>
        OSchema(SchemaType.Integer)
      case SSchema.SNumber =>
        OSchema(SchemaType.Number)
      case SSchema.SBoolean =>
        OSchema(SchemaType.Boolean)
      case SSchema.SString =>
        OSchema(SchemaType.String)
      case SSchema.SObject(_, fields, required) =>
        OSchema(SchemaType.Object).copy(
          required = Some(required.toList),
          properties = Some(
            fields.map {
              case (fieldName, fieldSchema) =>
                fieldName -> apply(fieldSchema)
            }.toMap
          )
        )
      case SSchema.SArray(el) =>
        OSchema(SchemaType.Array).copy(
          items = Some(apply(el))
        )
      case SSchema.SBinary() =>
        OSchema(SchemaType.String).copy(format = Some(SchemaFormat.Binary))
    }
  }
}
