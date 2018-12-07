package tapir.docs.openapi

import tapir.openapi.{Schema => OSchema, _}
import tapir.{Schema => SSchema}

object SSchemaToOSchema {
  def apply(schema: SSchema): OSchema = {
    schema match {
      case SSchema.SInt =>
        OSchema(SchemaType.Integer)
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
    }
  }
}
