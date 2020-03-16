package sttp.tapir.docs.openapi

import sttp.tapir.docs.openapi.schema.{ObjectSchemas, TypeData}
import sttp.tapir.openapi.{MediaType => OMediaType, _}
import sttp.tapir.{CodecFormat, EndpointIO, Schema => TSchema, _}

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

  def apply[T, CF <: CodecFormat](o: CodecForOptional[T, CF, _], examples: List[EndpointIO.Example[T]]): ListMap[String, OMediaType] = {
    val (singleExample, multipleExamples) = splitExamples(examples)

    ListMap(
      o.meta.format.mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(o)),
        singleExample.flatMap(example => exampleValue(o, example.value)),
        multipleExamples.zipWithIndex.map{
          case (example, i) =>
            example.name.getOrElse(s"Example$i") -> Right(Example(summary = example.summary, description = None, value = exampleValue(o, example.value), externalValue = None))
        }.toListMap
        ,
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
        example.map(ExampleSingleValue),
        ListMap.empty,
        ListMap.empty
      )
    )
  }

  def apply[CF <: CodecFormat](
                                schema: TSchema[_],
                                format: CF,
                                examples: List[EndpointIO.Example[String]]
                              ): ListMap[String, OMediaType] = {
    val (singleExample, multipleExamples) = splitExamples(examples)

    ListMap(
      format.mediaType.noCharset.toString -> OMediaType(
        Some(objectSchemas(TypeData(schema, Validator.pass))),
        singleExample.map(example => ExampleSingleValue(example.value)),
        multipleExamples.zipWithIndex.map{
          case (example, i) =>
            example.name.getOrElse(s"Example$i") -> Right(Example(summary = example.summary, description = None, value = Some(ExampleSingleValue(example.value)), externalValue = None))
        }.toListMap,
        ListMap.empty
      )
    )
  }

  private def splitExamples[T](examples: List[EndpointIO.Example[T]]): (Option[EndpointIO.Example[T]], List[EndpointIO.Example[T]]) =
    examples match {
      case (example@EndpointIO.Example(_, None, _)) :: Nil =>
        (Some(example), Nil)
      case Nil =>
        (None, Nil)
      case examples =>
        (None, examples)
    }
}
