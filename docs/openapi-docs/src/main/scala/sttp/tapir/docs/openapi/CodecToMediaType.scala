package sttp.tapir.docs.openapi

import sttp.tapir.docs.openapi.schema.{ObjectSchemas, TypeData}
import sttp.tapir.openapi.{MediaType => OMediaType}
import sttp.tapir.{CodecFormat, EndpointIO, Schema => TSchema, _}

import scala.collection.immutable.ListMap

private[openapi] class CodecToMediaType(objectSchemas: ObjectSchemas) {

  def apply[T, CF <: CodecFormat](o: CodecForOptional[T, CF, _], examples: List[EndpointIO.Example[T]]): ListMap[String, OMediaType] = {
    val convertedExamples = ExampleConverter.convertExamples(o, examples)

    ListMap(
      o.meta.format.mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(o)),
        convertedExamples.singleExample,
        convertedExamples.multipleExamples,
        ListMap.empty
      )
    )
  }

  def apply[CF <: CodecFormat](
      schema: TSchema[_],
      format: CF,
      examples: List[EndpointIO.Example[String]]
  ): ListMap[String, OMediaType] = {
    val convertedExamples = ExampleConverter.convertExamples(Codec.stringPlainCodecUtf8, examples)

    ListMap(
      format.mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(TypeData(schema, Validator.pass))),
        convertedExamples.singleExample,
        convertedExamples.multipleExamples,
        ListMap.empty
      )
    )
  }
}
