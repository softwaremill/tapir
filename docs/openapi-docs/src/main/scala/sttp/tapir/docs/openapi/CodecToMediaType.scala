package sttp.tapir.docs.openapi

import sttp.tapir.docs.apispec.schema.Schemas
import sttp.tapir.openapi.{MediaType => OMediaType}
import sttp.tapir.{CodecFormat, _}

import scala.collection.immutable.ListMap

private[openapi] class CodecToMediaType(objectSchemas: Schemas) {
  def apply[T, CF <: CodecFormat](o: Codec[_, T, CF], examples: List[EndpointIO.Example[T]]): ListMap[String, OMediaType] = {
    val convertedExamples = ExampleConverter.convertExamples(o, examples)

    ListMap(
      o.format.mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(o)),
        convertedExamples.singleExample,
        convertedExamples.multipleExamples
      )
    )
  }
}
