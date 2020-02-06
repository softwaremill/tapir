package sttp.tapir.codec.refined

import sttp.tapir._
import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.numeric.{Greater, GreaterEqual, Less, LessEqual}
import shapeless.Witness

import scala.reflect.ClassTag

trait TapirCodecRefined extends LowPriorityValidatorForPredicate {
  implicit def codecForRefined[V, P, CF <: CodecFormat, R](
      implicit tm: Codec[V, CF, R],
      refinedValidator: Validate[V, P],
      refinedValidatorTranslation: ValidatorForPredicate[V, P]
  ): Codec[V Refined P, CF, R] = {
    implicitly[Codec[V, CF, R]]
      .validate(refinedValidatorTranslation.validator) // in reality if this validator has to fail, it will fail before in mapDecode while trying to construct refined type
      .mapDecode { v: V =>
        refineV[P](v) match {
          case Right(refined) => DecodeResult.Value(refined)
          case Left(errorMessage) =>
            DecodeResult.InvalidValue(refinedValidatorTranslation.validationErrors(v, errorMessage))
        }
      }(_.value)
  }

  //

  implicit def validatorFromPredicate[V, P](implicit vfp: ValidatorForPredicate[V, P]): Validator[V Refined P] =
    vfp.validator.contramap(_.value)

  //

  implicit val validatorForNonEmptyString: ValidatorForPredicate[String, NonEmpty] =
    ValidatorForPredicate.fromPrimitiveValidator[String, NonEmpty](Validator.minLength(1))

  implicit def validatorForMatchesRegexp[S <: String](
      implicit ws: Witness.Aux[S]
  ): ValidatorForPredicate[String, MatchesRegex[S]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.pattern(ws.value))

  implicit def validatorForLess[N: Numeric, NM <: N](implicit ws: Witness.Aux[NM]): ValidatorForPredicate[N, Less[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(ws.value, exclusive = true))

  implicit def validatorForLessEqual[N: Numeric, NM <: N](
      implicit ws: Witness.Aux[NM]
  ): ValidatorForPredicate[N, LessEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(ws.value))

  implicit def validatorForGreater[N: Numeric, NM <: N](implicit ws: Witness.Aux[NM]): ValidatorForPredicate[N, Greater[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(ws.value, exclusive = true))

  implicit def validatorForGreaterEqual[N: Numeric, NM <: N](
      implicit ws: Witness.Aux[NM]
  ): ValidatorForPredicate[N, GreaterEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(ws.value))
}

trait ValidatorForPredicate[V, P] {
  def validator: Validator[V]
  def validationErrors(value: V, refinedErrorMessage: String): List[ValidationError[_]]
}

object ValidatorForPredicate {
  def fromPrimitiveValidator[V, P](v: Validator.Primitive[V]): ValidatorForPredicate[V, P] =
    new ValidatorForPredicate[V, P] {
      override def validator: Validator[V] = v
      override def validationErrors(value: V, refinedErrorMessage: String): List[ValidationError[_]] = List(ValidationError[V](v, value))
    }
}

trait LowPriorityValidatorForPredicate {
  implicit def genericValidatorForPredicate[V, P: ClassTag](
      implicit refinedValidator: Validate[V, P]
  ): ValidatorForPredicate[V, P] = new ValidatorForPredicate[V, P] {
    override val validator: Validator.Custom[V] = Validator.Custom(
      refinedValidator.isValid,
      implicitly[ClassTag[P]].runtimeClass.toString
    ) //for the moment there is no way to get a human description of a predicate/validator without having a concrete value to run it

    override def validationErrors(value: V, refinedErrorMessage: String): List[ValidationError[_]] =
      List(ValidationError[V](validator.copy(message = refinedErrorMessage), value))
  }
}
