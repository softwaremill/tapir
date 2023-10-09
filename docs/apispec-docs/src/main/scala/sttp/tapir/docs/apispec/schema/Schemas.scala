package sttp.tapir.docs.apispec.schema

import sttp.apispec.{SchemaType, Schema => ASchema}
import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

import scala.util.chaining._

/** Converts a tapir schema to an OpenAPI/AsyncAPI reference (if the schema is named), or to the appropriate schema. */
class Schemas(
    tschemaToASchema: TSchemaToASchema,
    toSchemaReference: ToSchemaReference,
    markOptionsAsNullable: Boolean
) {
  def apply[T](codec: Codec[T, _, _]): ASchema = apply(codec.schema)

  def apply(schema: TSchema[_]): ASchema = {
    schema.name match {
      case Some(name) => toSchemaReference.map(schema, name)
      case None =>
        schema.schemaType match {
          case TSchemaType.SArray(nested @ TSchema(_, Some(name), isOptional, _, _, _, _, _, _, _, _)) =>
            ASchema(SchemaType.Array)
              .copy(items = Some(toSchemaReference.map(nested, name)))
              .pipe(s => if (isOptional && markOptionsAsNullable) s.copy(nullable = Some(true)) else s)
          case TSchemaType.SOption(ts) => apply(ts)
          case _                       => tschemaToASchema(schema)
        }
    }
  }
}
