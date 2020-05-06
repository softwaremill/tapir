package sttp.tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import sttp.tapir.{Schema, SchemaType}
import sttp.tapir._

import scala.collection.immutable.SortedSet

trait TapirCodecCats {

  private def nonEmpty[T, C[X] <: Iterable[X]]: Validator.Primitive[C[T]] = Validator.minSize[T, C](1)

  private def iterableAndNonEmpty[T, C[X] <: Iterable[X]](implicit v: Validator[T]): Validator[C[T]] =
    v.asIterableElements[C].and(nonEmpty)

  implicit def validatorNel[T: Validator]: Validator[NonEmptyList[T]] =
    iterableAndNonEmpty[T, List].contramap(_.toList)

  implicit def validatorNec[T: Validator]: Validator[NonEmptyChain[T]] =
    iterableAndNonEmpty[T, List].contramap(_.toChain.toList)

  implicit def validatorNes[T: Validator]: Validator[NonEmptySet[T]] =
    iterableAndNonEmpty[T, Set].contramap(_.toSortedSet)

  implicit def schemaForNel[T: Schema]: Schema[NonEmptyList[T]] =
    Schema[NonEmptyList[T]](SchemaType.SArray(implicitly[Schema[T]])).copy(isOptional = false)

  implicit def schemaForNec[T: Schema]: Schema[NonEmptyChain[T]] =
    Schema[NonEmptyChain[T]](SchemaType.SArray(implicitly[Schema[T]])).copy(isOptional = false)

  implicit def schemaForNes[T: Schema]: Schema[NonEmptySet[T]] =
    Schema[NonEmptySet[T]](SchemaType.SArray(implicitly[Schema[T]])).copy(isOptional = false)

  implicit def codecForNonEmptyList[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, NonEmptyList[H], CF] =
    c.modifySchema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { l => DecodeResult.fromOption(NonEmptyList.fromList(l)) }(_.toList)

  implicit def codecForNonEmptyChain[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, NonEmptyChain[H], CF] =
    c.modifySchema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { l => DecodeResult.fromOption(NonEmptyChain.fromSeq(l)) }(_.toNonEmptyList.toList)

  implicit def codecForNonEmptySet[L, H: Ordering, CF <: CodecFormat](implicit c: Codec[L, Set[H], CF]): Codec[L, NonEmptySet[H], CF] =
    c.modifySchema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { set => DecodeResult.fromOption(NonEmptySet.fromSet(SortedSet(set.toSeq: _*))) }(_.toSortedSet)
}
