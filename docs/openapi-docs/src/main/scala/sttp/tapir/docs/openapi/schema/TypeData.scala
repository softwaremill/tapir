package sttp.tapir.docs.openapi.schema

import sttp.tapir.{Codec, Validator, Schema => TSchema, SchemaType => TSchemaType}

case class TypeData[T](schema: TSchema[_], validator: Validator[T])

object TypeData {
  def apply[T](tm: Codec[_, T, _]): TypeData[T] = TypeData(tm.schema.getOrElse(TSchema(TSchemaType.SBinary)), tm.validator)
}
