package sttp.tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import sttp.tapir.{Schema, SchemaType}
import sttp.tapir._

trait TapirCodecCats {
  private def nonEmptyValidator[T]: Validator[List[T]] = Validator.minSize[T, List](1)

  implicit def validatorNel[T](implicit v: Validator[T]): Validator[NonEmptyList[T]] =
    v.asIterableElements.and(nonEmptyValidator[T]).contramap(_.toList)

  implicit def validatorNec[T](implicit v: Validator[T]): Validator[NonEmptyChain[T]] =
    v.asIterableElements.and(nonEmptyValidator[T]).contramap(_.toChain.toList)

  implicit def validatorNes[T](implicit v: Validator[T]): Validator[NonEmptySet[T]] =
    v.asIterableElements.and(nonEmptyValidator[T]).contramap(_.toSortedSet.toList)

  implicit def schemaForNel[T: Schema]: Schema[NonEmptyList[T]] = Schema[NonEmptyList[T]](SchemaType.SArray(implicitly[Schema[T]]))

  implicit def schemaForNec[T: Schema]: Schema[NonEmptyChain[T]] = Schema[NonEmptyChain[T]](SchemaType.SArray(implicitly[Schema[T]]))

  implicit def schemaForNes[T: Schema]: Schema[NonEmptySet[T]] = Schema[NonEmptySet[T]](SchemaType.SArray(implicitly[Schema[T]]))
}
