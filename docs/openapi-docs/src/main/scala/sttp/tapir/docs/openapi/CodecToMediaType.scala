package sttp.tapir.docs.openapi

import sttp.apispec.openapi.{MediaType => OMediaType}
import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.{CodecFormat, _}

import scala.collection.immutable.ListMap

private[openapi] class CodecToMediaType(schemas: Schemas) {
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
        Some(schemas(o)),
        allExamples.singleExample,
        allExamples.multipleExamples
      )
    )
  }
}
