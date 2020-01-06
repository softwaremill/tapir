package sttp.tapir.docs.openapi

import sttp.tapir.docs.openapi.schema.{ObjectSchemas, TypeData}
import sttp.tapir.openapi.{MediaType => OMediaType, _}
import sttp.tapir.{CodecFormat, Schema => TSchema, _}

import scala.collection.immutable.ListMap

private[openapi] class CodecToMediaType(objectSchemas: ObjectSchemas) {
  def apply[T, CF <: CodecFormat](o: CodecForOptional[T, CF, _], example: Option[T]): ListMap[String, OMediaType] = {
    ListMap(
      o.meta.format.mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(o)),
        example.flatMap(exampleValue(o, _)),
        ListMap.empty,
        ListMap.empty
      )
    )
  }

  def apply[CF <: CodecFormat](
      schema: TSchema[_],
      format: CF,
      example: Option[String]
  ): ListMap[String, OMediaType] = {
    ListMap(
      format.mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(TypeData(schema, Validator.pass))),
        example.map(ExampleValue),
        ListMap.empty,
        ListMap.empty
      )
    )
  }
}
