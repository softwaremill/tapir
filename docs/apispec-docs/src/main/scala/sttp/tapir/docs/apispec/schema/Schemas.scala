package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.{ReferenceOr, SchemaType, Schema => ASchema}
import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

class Schemas(
    tschemaToASchema: TSchemaToASchema,
    objectToSchemaReference: ObjectToSchemaReference
) {
  def apply[T](codec: Codec[T, _, _]): ReferenceOr[ASchema] = apply(codec.schema)

  def apply(schema: TSchema[_]): ReferenceOr[ASchema] = {
    schema.schemaType match {
      case TSchemaType.SArray(TSchema(o: TSchemaType.SObject, _, _, _, _, _, _, _)) =>
        Right(ASchema(SchemaType.Array).copy(items = Some(Left(objectToSchemaReference.map(o.info)))))
      case o: TSchemaType.SObject => Left(objectToSchemaReference.map(o.info))
      case _                      => tschemaToASchema(schema)
    }
  }
}
