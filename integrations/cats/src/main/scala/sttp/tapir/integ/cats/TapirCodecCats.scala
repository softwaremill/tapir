package sttp.tapir.integ.cats

import cats.data.{Chain, NonEmptyChain, NonEmptyList, NonEmptySet, NonEmptyVector}
import sttp.tapir._
import sttp.tapir.integ.cats.ValidatorCats.nonEmptyFoldable
import sttp.tapir.Validator.nonEmpty

import scala.collection.immutable.SortedSet

trait TapirCodecCats {

  implicit def schemaForNel[T: Schema]: Schema[NonEmptyList[T]] =
    Schema[NonEmptyList[T]](SchemaType.SArray(implicitly[Schema[T]])(_.toList))
      .validate(nonEmptyFoldable)

  implicit def schemaForNev[T: Schema]: Schema[NonEmptyVector[T]] =
    Schema[NonEmptyVector[T]](SchemaType.SArray(implicitly[Schema[T]])(_.toVector))
      .validate(ValidatorCats.nonEmptyFoldable)

  implicit def schemaForChain[T: Schema]: Schema[Chain[T]] =
    implicitly[Schema[List[T]]].map(l => Option(Chain.fromSeq(l)))(_.toList)

  implicit def schemaForNec[T: Schema]: Schema[NonEmptyChain[T]] =
    Schema[NonEmptyChain[T]](SchemaType.SArray(implicitly[Schema[T]])(_.toChain.toList))
      .validate(nonEmptyFoldable[NonEmptyChain, T])

  implicit def schemaForNes[T: Schema]: Schema[NonEmptySet[T]] =
    Schema[NonEmptySet[T]](SchemaType.SArray(implicitly[Schema[T]])(_.toSortedSet))
      .validate(nonEmptyFoldable[NonEmptySet, T])

  implicit def codecForNonEmptyList[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, NonEmptyList[H], CF] =
    c.schema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { l => DecodeResult.fromOption(NonEmptyList.fromList(l)) }(_.toList)

  implicit def codecForNonEmptyVector[L, H, CF <: CodecFormat](implicit c: Codec[L, Vector[H], CF]): Codec[L, NonEmptyVector[H], CF] =
    c.schema(_.copy(isOptional = false))
      .validate(Validator.nonEmpty)
      .mapDecode { v => DecodeResult.fromOption(NonEmptyVector.fromVector(v)) }(_.toVector)

  implicit def codecForChain[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, Chain[H], CF] =
    c.map(Chain.fromSeq(_))(_.toList)

  implicit def codecForNonEmptyChain[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, NonEmptyChain[H], CF] =
    c.schema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { l => DecodeResult.fromOption(NonEmptyChain.fromSeq(l)) }(_.toNonEmptyList.toList)

  implicit def codecForNonEmptySet[L, H: Ordering, CF <: CodecFormat](implicit c: Codec[L, Set[H], CF]): Codec[L, NonEmptySet[H], CF] =
    c.schema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { set => DecodeResult.fromOption(NonEmptySet.fromSet(SortedSet(set.toSeq: _*))) }(_.toSortedSet)
}
