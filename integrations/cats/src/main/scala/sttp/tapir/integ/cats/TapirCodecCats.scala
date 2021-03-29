package sttp.tapir.integ.cats

import cats.data.{Chain, NonEmptyChain, NonEmptyList, NonEmptySet}
import sttp.tapir._

import scala.collection.immutable.SortedSet

trait TapirCodecCats {

  private def nonEmpty[T, C[X] <: Iterable[X]]: Validator.Primitive[C[T]] = Validator.minSize[T, C](1)

  implicit def schemaForNel[T: Schema]: Schema[NonEmptyList[T]] =
    Schema[NonEmptyList[T]](SchemaType.SArray(implicitly[Schema[T]])(_.toList)).validate(nonEmpty.contramap(_.toList))

  implicit def schemaForChain[T: Schema]: Schema[Chain[T]] =
    implicitly[Schema[List[T]]].map(l => Option(Chain.fromSeq(l)))(_.toList)

  implicit def schemaForNec[T: Schema]: Schema[NonEmptyChain[T]] =
    Schema[NonEmptyChain[T]](SchemaType.SArray(implicitly[Schema[T]])(_.toChain.toList)).validate(nonEmpty.contramap(_.toChain.toList))

  implicit def schemaForNes[T: Schema]: Schema[NonEmptySet[T]] =
    Schema[NonEmptySet[T]](SchemaType.SArray(implicitly[Schema[T]])(_.toSortedSet)).validate(nonEmpty.contramap(_.toSortedSet))

  implicit def codecForNonEmptyList[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, NonEmptyList[H], CF] =
    c.modifySchema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { l => DecodeResult.fromOption(NonEmptyList.fromList(l)) }(_.toList)

  implicit def codecForChain[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, Chain[H], CF] =
    c.map(Chain.fromSeq(_))(_.toList)

  implicit def codecForNonEmptyChain[L, H, CF <: CodecFormat](implicit c: Codec[L, List[H], CF]): Codec[L, NonEmptyChain[H], CF] =
    c.modifySchema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { l => DecodeResult.fromOption(NonEmptyChain.fromSeq(l)) }(_.toNonEmptyList.toList)

  implicit def codecForNonEmptySet[L, H: Ordering, CF <: CodecFormat](implicit c: Codec[L, Set[H], CF]): Codec[L, NonEmptySet[H], CF] =
    c.modifySchema(_.copy(isOptional = false))
      .validate(nonEmpty)
      .mapDecode { set => DecodeResult.fromOption(NonEmptySet.fromSet(SortedSet(set.toSeq: _*))) }(_.toSortedSet)
}
