package sttp.tapir.docs.openapi

import sttp.apispec._
import sttp.apispec.openapi.{Example, ReferenceOr}
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.{Codec, EndpointIO, Schema => TSchema}

import scala.collection.immutable.ListMap

private[openapi] object ExampleConverter {
  case class Examples(singleExample: Option[ExampleValue], multipleExamples: ListMap[String, ReferenceOr[Example]]) {
    def +(other: Examples): Examples = Examples(
      singleExample.orElse(other.singleExample),
      multipleExamples ++ other.multipleExamples
    )
  }

  def convertExamples[T](o: Codec[_, T, _], examples: List[EndpointIO.Example[T]]): Examples =
    convertExamples(examples)(exampleValue(o, _))

  def convertExamples(s: TSchema[_], examples: List[EndpointIO.Example[_]]): Examples =
    convertExamples[Any](examples)(exampleValue(s, _))

  private def convertExamples[T](examples: List[EndpointIO.Example[T]])(exampleValue: T => Option[ExampleValue]): Examples = {
    examples match {
      case (example @ EndpointIO.Example(_, None, None, None)) :: Nil =>
        Examples(exampleValue(example.value), ListMap.empty)
      case (example @ EndpointIO.Example(_, _, _, _)) :: Nil if example.name.isDefined || example.summary.isDefined =>
        Examples(
          None,
          ListMap(
            example.name.getOrElse("Example") -> Right(
              Example(summary = example.summary, description = example.description, value = exampleValue(example.value))
            )
          )
        )
      case examples =>
        val exampleValues = examples.zipWithIndex.map { case (example, i) =>
          example.name.getOrElse(s"Example$i") ->
            Right(Example(summary = example.summary, description = example.description, value = exampleValue(example.value)))
        }.toListMap
        Examples(None, exampleValues)
    }
  }
}
