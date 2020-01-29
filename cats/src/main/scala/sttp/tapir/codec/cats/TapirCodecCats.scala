package sttp.tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import sttp.tapir.{Schema, SchemaType}
import sttp.tapir._

import scala.collection.immutable.SortedSet

trait TapirCodecCats {
  private def nonEmptyValidator[T]: Validator[List[T]] = Validator.minSize[T, List](1)

  implicit def validatorNel[T](implicit v: Validator[T]): Validator[NonEmptyList[T]] =
    v.asIterableElements.and(nonEmptyValidator[T]).contramap(_.toList)

  implicit def validatorNec[T](implicit v: Validator[T]): Validator[NonEmptyChain[T]] =
    v.asIterableElements.and(nonEmptyValidator[T]).contramap(_.toChain.toList)

  implicit def validatorNes[T](implicit v: Validator[T]): Validator[NonEmptySet[T]] =
    v.asIterableElements.and(nonEmptyValidator[T]).contramap(_.toSortedSet.toList)

  implicit def schemaForNel[T: Schema]: Schema[NonEmptyList[T]] =
    Schema[NonEmptyList[T]](SchemaType.SArray(implicitly[Schema[T]])).copy(isOptional = false)

  implicit def schemaForNec[T: Schema]: Schema[NonEmptyChain[T]] =
    Schema[NonEmptyChain[T]](SchemaType.SArray(implicitly[Schema[T]])).copy(isOptional = false)

  implicit def schemaForNes[T: Schema]: Schema[NonEmptySet[T]] =
    Schema[NonEmptySet[T]](SchemaType.SArray(implicitly[Schema[T]])).copy(isOptional = false)

  implicit def codecForNonEmptyList[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[NonEmptyList[T], CF, R] =
    implicitly[CodecForMany[List[T], CF, R]]
      .mapDecode { l =>
        DecodeResult.fromOption(NonEmptyList.fromList(l))
      }(_.toList)

  implicit def codecForNonEmptyChain[T, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[NonEmptyChain[T], CF, R] =
    implicitly[CodecForMany[List[T], CF, R]]
      .mapDecode { l =>
        DecodeResult.fromOption(NonEmptyChain.fromSeq(l))
      }(_.toNonEmptyList.toList)

  implicit def codecForNonEmptySet[T: Ordering, CF <: CodecFormat, R](implicit tm: Codec[T, CF, R]): CodecForMany[NonEmptySet[T], CF, R] =
    implicitly[CodecForMany[Set[T], CF, R]]
      .mapDecode { set =>
        DecodeResult.fromOption(NonEmptySet.fromSet(SortedSet(set.toSeq: _*)))
      }(_.toSortedSet)
}
