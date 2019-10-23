package tapir.codec.cats

import cats.data.{NonEmptyChain, NonEmptyList, NonEmptySet}
import tapir._

trait TapirCodecCats {
  private def nonEmptyValidator[T: Validator]: Validator.Primitive[List[T]] = Validator.minSize[T, List](1)

  implicit def validatorNel[T: Validator]: Validator[NonEmptyList[T]] =
    nonEmptyValidator[T].contramap(_.toList)

  implicit def validatorNec[T: Validator]: Validator[NonEmptyChain[T]] =
    nonEmptyValidator[T].contramap(_.toChain.toList)

  implicit def validatorNes[T: Validator]: Validator[NonEmptySet[T]] =
    nonEmptyValidator[T].contramap(_.toSortedSet.toList)

  implicit def schemaForNel[T: SchemaFor]: SchemaFor[NonEmptyList[T]] = new SchemaFor[NonEmptyList[T]] {
    def schema: Schema = Schema.SArray(implicitly[SchemaFor[T]].schema)
  }

  implicit def schemaForNec[T: SchemaFor]: SchemaFor[NonEmptyChain[T]] = new SchemaFor[NonEmptyChain[T]] {
    def schema: Schema = Schema.SArray(implicitly[SchemaFor[T]].schema)
  }

  implicit def schemaForNes[T: SchemaFor]: SchemaFor[NonEmptySet[T]] = new SchemaFor[NonEmptySet[T]] {
    def schema: Schema = Schema.SArray(implicitly[SchemaFor[T]].schema)
  }
}
