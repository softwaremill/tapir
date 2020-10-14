package sttp.tapir.docs.apispec.schema

import sttp.tapir.apispec.{ReferenceOr, SchemaType, Schema => ASchema}
import sttp.tapir.{Codec, Schema => TSchema, SchemaType => TSchemaType}

class ObjectSchemas(
    tschemaToASchema: TSchemaToASchema,
    schemaReferenceMapper: SchemaReferenceMapper
) {
  def apply[T](codec: Codec[T, _, _]): ReferenceOr[ASchema] = apply(TypeData(codec))

  def apply(typeData: TypeData[_]): ReferenceOr[ASchema] = {
    typeData.schema.schemaType match {
      case TSchemaType.SArray(TSchema(o: TSchemaType.SObject, _, _, _, _)) =>
        Right(ASchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(o.info)))))
      case o: TSchemaType.SObject => Left(schemaReferenceMapper.map(o.info))
      case _                      => tschemaToASchema(typeData)
    }
  }
}
