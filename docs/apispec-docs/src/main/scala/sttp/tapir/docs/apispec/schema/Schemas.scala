package sttp.tapir.docs.apispec.schema

import sttp.apispec.{ReferenceOr, SchemaType, Schema => ASchema}
import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

/** Converts a tapir schema to an OpenAPI/AsyncAPI reference (if the schema is named), or to the appropriate schema. */
class Schemas(
    tschemaToASchema: TSchemaToASchema,
    toSchemaReference: ToSchemaReference,
    markOptionsAsNullable: Boolean
) {
  def apply[T](codec: Codec[T, _, _]): ReferenceOr[ASchema] = apply(codec.schema)

  def apply(schema: TSchema[_]): ReferenceOr[ASchema] = {
    SchemaKey(schema) match {
      case Some(key) => Left(toSchemaReference.map(key))
      case None =>
        schema.schemaType match {
          case TSchemaType.SArray(nested @ TSchema(_, Some(name), isOptional, _, _, _, _, _, _, _, _)) =>
            Right(ASchema(SchemaType.Array).copy(items = Some(Left(toSchemaReference.map(SchemaKey(nested, name))))))
              .map(s => if (isOptional && markOptionsAsNullable) s.copy(nullable = Some(true)) else s)
          case TSchemaType.SOption(ts) => apply(ts)
          case _                       => tschemaToASchema(schema)
        }
    }
  }
}
