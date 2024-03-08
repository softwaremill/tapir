package sttp.tapir.docs.apispec.schema

import sttp.apispec.{SchemaType, Schema => ASchema}
import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

/** Converts a tapir schema to an OpenAPI/AsyncAPI reference (if the schema is named), or to the appropriate schema. */
class Schemas(
    tschemaToASchema: TSchemaToASchema,
    toSchemaReference: ToSchemaReference,
    markOptionsAsNullable: Boolean
) {
  def apply[T](codec: Codec[T, _, _]): ASchema = apply(codec.schema)

  def apply(schema: TSchema[_]): ASchema = tschemaToASchema(schema, true)
}
