package tapir.docs.openapi.schema

import tapir.{Codec, CodecForMany, CodecForOptional, Validator, Schema => TSchema}

case class TypeData[T](schema: TSchema[_], validator: Validator[T])

object TypeData {
  def apply[T](tm: Codec[T, _, _]): TypeData[T] = {
    TypeData(tm.meta.schema, tm.validator)
  }
  def apply[T](tm: CodecForOptional[T, _, _]): TypeData[T] = {
    TypeData(tm.meta.schema, tm.validator)
  }
  def apply[T](tm: CodecForMany[T, _, _]): TypeData[T] = {
    TypeData(tm.meta.schema, tm.validator)
  }
}
