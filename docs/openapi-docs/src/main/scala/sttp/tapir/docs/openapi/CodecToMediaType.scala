package sttp.tapir.docs.openapi

import sttp.apispec.openapi.{MediaType => OMediaType}
import sttp.tapir.docs.apispec.schema.TSchemaToASchema
import sttp.tapir.{CodecFormat, _}

import scala.collection.immutable.ListMap

private[openapi] class CodecToMediaType(tschemaToASchema: TSchemaToASchema) {
  def apply[T, CF <: CodecFormat](
      o: Codec[_, T, CF],
      examples: List[EndpointIO.Example[T]],
      forcedContentType: Option[String],
      additionalEncodedExamples: List[EndpointIO.Example[Any]]
  ): ListMap[String, OMediaType] = {
    val convertedExamples = ExampleConverter.convertExamples(o, examples)
    val additionalExamples = ExampleConverter.convertExamples(o.schema, additionalEncodedExamples)
    val allExamples = convertedExamples + additionalExamples

    ListMap(
      forcedContentType.getOrElse(o.format.mediaType.noCharset.toString) -> OMediaType(
        Some(tschemaToASchema(o)),
        allExamples.singleExample,
        allExamples.multipleExamples
      )
    )
  }
}
