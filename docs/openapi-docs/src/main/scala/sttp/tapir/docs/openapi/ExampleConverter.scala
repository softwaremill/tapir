package sttp.tapir.docs.openapi

import sttp.tapir.apispec._
import sttp.tapir.docs.apispec.exampleValue
import sttp.tapir.internal.IterableToListMap
import sttp.tapir.openapi.Example
import sttp.tapir.{Codec, EndpointIO}

import scala.collection.immutable.ListMap

private[openapi] object ExampleConverter {
  case class Examples(singleExample: Option[ExampleValue], multipleExamples: ListMap[String, ReferenceOr[Example]])

  def convertExamples[T](o: Codec[_, T, _], examples: List[EndpointIO.Example[T]]): Examples = {
    convertExamples(examples)(exampleValue(o, _))
  }

  private def convertExamples[T](examples: List[EndpointIO.Example[T]])(exampleValue: T => Option[ExampleValue]): Examples = {
    examples match {
      case (example @ EndpointIO.Example(_, None, _)) :: Nil =>
        Examples(exampleValue(example.value), ListMap.empty)
      case examples =>
        val exampleValues = examples.zipWithIndex.map { case (example, i) =>
          example.name.getOrElse(s"Example$i") ->
            Right(Example(summary = example.summary, value = exampleValue(example.value)))
        }.toListMap
        Examples(None, exampleValues)
    }
  }
}
