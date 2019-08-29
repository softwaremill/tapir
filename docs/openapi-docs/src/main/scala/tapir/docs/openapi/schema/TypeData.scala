package tapir.docs.openapi.schema

import tapir.docs.openapi._
import tapir.{Codec, CodecForMany, CodecForOptional, Validator, Schema => TSchema}

case class TypeData[TS <: TSchema, T](schema: TS, validator: Validator[T], encode: EncodeToAny[T] = (_: T) => None)

object TypeData {
  def apply[T](tm: Codec[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.validator, encodeAny(tm))
  }
  def apply[T](tm: CodecForOptional[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.validator, encodeAny(tm))
  }
  def apply[T](tm: CodecForMany[T, _, _]): AnyTypeData[T] = {
    TypeData(tm.meta.schema, tm.validator, encodeAny(tm))
  }
}
