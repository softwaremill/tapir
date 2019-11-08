package tapir.docs.openapi.schema

import tapir.{Codec, CodecForMany, CodecForOptional, Validator, Schema => TSchema, SchemaType => TSchemaType}

// TODO
case class TypeData[TS <: TSchemaType, T](schema: TSchema[_], schemaType: TS, validator: Validator[T])

object TypeData {
  def apply[T](tm: Codec[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.meta.schema.schemaType, tm.validator)
  }
  def apply[T](tm: CodecForOptional[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.meta.schema.schemaType, tm.validator)
  }
  def apply[T](tm: CodecForMany[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.meta.schema.schemaType, tm.validator)
  }
}
