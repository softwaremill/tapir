package tapir.docs.openapi.schema

import tapir.openapi.OpenAPI.ReferenceOr
import tapir.openapi.{SchemaType, Schema => OSchema}
import tapir.{Codec, CodecForMany, CodecForOptional, Schema => TSchema}

class ObjectSchemas(
    tschemaToOSchema: TSchemaToOSchema,
    schemaReferenceMapper: SchemaReferenceMapper
) {
  def apply[T](codec: CodecForMany[T, _, _]): ReferenceOr[OSchema] = apply(TypeData(codec))
  def apply[T](codec: Codec[T, _, _]): ReferenceOr[OSchema] = apply(TypeData(codec))
  def apply[T](codec: CodecForOptional[T, _, _]): ReferenceOr[OSchema] = apply(TypeData(codec))

  def apply(typeData: TypeData[_, _]): ReferenceOr[OSchema] = {
    typeData.schema match {
      case TSchema.SArray(o: TSchema.SObject) =>
        Right(OSchema(SchemaType.Array).copy(items = Some(Left(schemaReferenceMapper.map(o.info)))))
      case o: TSchema.SObject => Left(schemaReferenceMapper.map(o.info))
      case _                  => tschemaToOSchema(typeData)
    }
  }
}
