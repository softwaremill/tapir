package sttp.tapir.codec.refined

import sttp.tapir._
import eu.timepit.refined.api.{Max, Refined, Validate}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.numeric.{Greater, GreaterEqual, Less, LessEqual}
import shapeless.Witness

import scala.reflect.ClassTag

trait RefinedValidatorTranslation[V, P] {
  def tapirValidator: Validator[V]
  def listError(value: V, refinedErrorMessage: String): List[ValidationError[_]]
}

object RefinedValidatorTranslation {
  def fromPrimitiveValidator[V, P](validator: Validator.Primitive[V]) = new RefinedValidatorTranslation[V, P] {
    override def tapirValidator: Validator[V] = validator
    override def listError(value: V, refinedErrorMessage: String): List[ValidationError[_]] = List(ValidationError[V](validator, value))
  }
}

trait TapirCodecRefined extends ImplicitGenericRefinedValidator {
  implicit def codecForRefined[V, P, CF <: CodecFormat, R](implicit tm: Codec[V, CF, R], refinedValidator: Validate[V, P], refinedValidatorTranslation: RefinedValidatorTranslation[V, P]): Codec[V Refined P, CF, R] = {
    implicitly[Codec[V, CF, R]]
      .validate(refinedValidatorTranslation.tapirValidator) // in reality if this validator has to fail, it will fail before in mapDecode while trying to construct refined type
      .mapDecode { v: V =>
        refineV[P](v) match {
          case Right(refined) => DecodeResult.Value(refined)
          case Left(errorMessage) => {
            DecodeResult.InvalidValue(refinedValidatorTranslation.listError(v, errorMessage))
          }
        }
      }(_.value)
  }

  implicit val nonEmptyStringRefinedTranslator: RefinedValidatorTranslation[String, NonEmpty] =
    RefinedValidatorTranslation.fromPrimitiveValidator[String, NonEmpty](Validator.minLength(1))

  implicit def matchesRegexRefinedTranslator[S <: String](implicit ws: Witness.Aux[S]): RefinedValidatorTranslation[String, MatchesRegex[S]] =
    RefinedValidatorTranslation.fromPrimitiveValidator(Validator.pattern(ws.value))

  implicit def lessRefinedTranslator[N: Numeric, NM <: N](implicit ws: Witness.Aux[NM]): RefinedValidatorTranslation[N, Less[NM]] =
    RefinedValidatorTranslation.fromPrimitiveValidator(Validator.max(ws.value, exclusive = true))

  implicit def lessEqualRefinedTranslator[N: Numeric, NM <: N](implicit ws: Witness.Aux[NM]): RefinedValidatorTranslation[N, LessEqual[NM]] =
    RefinedValidatorTranslation.fromPrimitiveValidator(Validator.max(ws.value, exclusive = false))

  implicit def maxRefinedTranslator[N: Numeric, NM <: N](implicit ws: Witness.Aux[NM]): RefinedValidatorTranslation[N, Greater[NM]] =
    RefinedValidatorTranslation.fromPrimitiveValidator(Validator.min(ws.value, exclusive = true))

  implicit def maxEqualRefinedTranslator[N: Numeric, NM <: N](implicit ws: Witness.Aux[NM]): RefinedValidatorTranslation[N, GreaterEqual[NM]] =
    RefinedValidatorTranslation.fromPrimitiveValidator(Validator.min(ws.value, exclusive = false))
}

trait ImplicitGenericRefinedValidator {
  implicit def genericRefinedValidatorTranslation[V, P: ClassTag](implicit refinedValidator: Validate[V, P]): RefinedValidatorTranslation[V, P] = new RefinedValidatorTranslation[V, P] {
    override val tapirValidator: Validator.Custom[V] = Validator.Custom(
      refinedValidator.isValid(_),
      implicitly[ClassTag[P]].runtimeClass.toString) //for the moment there is no way to get a human description of a predicate/validator without having a concrete value to run it

    override def listError(value: V, refinedErrorMessage: String): List[ValidationError[_]] = List(ValidationError[V](tapirValidator.copy(message = refinedErrorMessage), value))
  }
}
