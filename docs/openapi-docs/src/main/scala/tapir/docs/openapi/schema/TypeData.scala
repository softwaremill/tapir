package tapir.docs.openapi.schema

import tapir.{Codec, CodecForMany, CodecForOptional, Validator, Schema => TSchema}

case class TypeData[TS <: TSchema, T](schema: TS, validator: Validator[T])

object TypeData {
  def apply[T](tm: Codec[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.validator)
  }
  def apply[T](tm: CodecForOptional[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.validator)
  }
  def apply[T](tm: CodecForMany[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.validator)
  }
}
