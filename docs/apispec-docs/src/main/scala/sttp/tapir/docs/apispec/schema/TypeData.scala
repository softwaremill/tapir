package sttp.tapir.docs.apispec.schema

import sttp.tapir.{Codec, Validator, Schema => TSchema}

case class TypeData[T](schema: TSchema[_], validator: Validator[T])

object TypeData {
  def apply[T](tm: Codec[_, T, _]): TypeData[T] = TypeData(tm.schema, tm.validator)
}
