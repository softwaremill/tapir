package sttp.tapir.codec.refined

import eu.timepit.refined.api.{Refined, Validate}
import eu.timepit.refined.boolean.{And, Or}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.internal.WitnessAs
import eu.timepit.refined.numeric.{Greater, GreaterEqual, Less, LessEqual}
import eu.timepit.refined.refineV
import eu.timepit.refined.string.{MatchesRegex, Uuid}
import shapeless.Witness
import sttp.tapir._

import scala.reflect.ClassTag

trait TapirCodecRefined extends LowPriorityValidatorForPredicate {
  implicit def refinedTapirSchema[V, P](implicit
      vSchema: Schema[V],
      refinedValidator: Validate[V, P],
      refinedValidatorTranslation: ValidatorForPredicate[V, P]
  ): Schema[V Refined P] =
    vSchema.validate(refinedValidatorTranslation.validator).map[V Refined P](v => refineV[P](v).toOption)(_.value)

  implicit def codecForRefined[R, V, P, CF <: CodecFormat](implicit
      tm: Codec[R, V, CF],
      refinedValidator: Validate[V, P],
      refinedValidatorTranslation: ValidatorForPredicate[V, P]
  ): Codec[R, V Refined P, CF] = {
    implicitly[Codec[R, V, CF]]
      .validate(
        refinedValidatorTranslation.validator
      ) // in reality if this validator has to fail, it will fail before in mapDecode while trying to construct refined type
      .mapDecode { (v: V) =>
        refineV[P](v) match {
          case Right(refined) => DecodeResult.Value(refined)
          case Left(errorMessage) =>
            DecodeResult.InvalidValue(refinedValidatorTranslation.validationErrors(v, errorMessage))
        }
      }(_.value)
  }

  implicit def uuidTapirSchema(implicit
      vSchema: Schema[String],
      refinedValidator: Validate[String, Uuid],
      refinedValidatorTranslation: ValidatorForPredicate[String, Uuid]
  ): Schema[String Refined Uuid] =
    refinedTapirSchema[String, Uuid].format("uuid")

  implicit val validatorForNonEmptyString: PrimitiveValidatorForPredicate[String, NonEmpty] =
    ValidatorForPredicate.fromPrimitiveValidator[String, NonEmpty](Validator.minLength(1))

  implicit def validatorForMatchesRegexp[S <: String](implicit
      ws: Witness.Aux[S]
  ): PrimitiveValidatorForPredicate[String, MatchesRegex[S]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.pattern(ws.value))

  implicit def validatorForLess[N: Numeric, NM](implicit ws: WitnessAs[NM, N]): PrimitiveValidatorForPredicate[N, Less[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(ws.snd, exclusive = true))

  implicit def validatorForLessEqual[N: Numeric, NM](implicit
      ws: WitnessAs[NM, N]
  ): PrimitiveValidatorForPredicate[N, LessEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(ws.snd))

  implicit def validatorForGreater[N: Numeric, NM](implicit ws: WitnessAs[NM, N]): PrimitiveValidatorForPredicate[N, Greater[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(ws.snd, exclusive = true))

  implicit def validatorForGreaterEqual[N: Numeric, NM](implicit
      ws: WitnessAs[NM, N]
  ): PrimitiveValidatorForPredicate[N, GreaterEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(ws.snd))

  implicit def validatorForAnd[N, LP, RP](implicit
      leftPredValidator: PrimitiveValidatorForPredicate[N, LP],
      rightPredValidator: PrimitiveValidatorForPredicate[N, RP],
      leftRefinedValidator: Validate[N, LP],
      rightRefinedValidator: Validate[N, RP]
  ): ValidatorForPredicate[N, LP And RP] =
    new ValidatorForPredicate[N, LP And RP] {
      override def validator: Validator[N] = Validator.all(leftPredValidator.validator, rightPredValidator.validator)
      override def validationErrors(value: N, refinedErrorMessage: String): List[ValidationError[_]] = {
        val primitivesErrors = Seq[(Validate[N, _], Validator.Primitive[N])](
          leftRefinedValidator -> leftPredValidator.validator,
          rightRefinedValidator -> rightPredValidator.validator
        )
          .filter { case (refinedValidator, _) => refinedValidator.notValid(value) }
          .map { case (_, primitiveValidator) => ValidationError.Primitive[N](primitiveValidator, value) }
          .toList

        if (primitivesErrors.isEmpty) {
          //this should not happen
          List(ValidationError.Custom(value, refinedErrorMessage, List()))
        } else {
          primitivesErrors
        }
      }
    }

  implicit def validatorForOr[N, LP, RP](implicit
      leftPredValidator: PrimitiveValidatorForPredicate[N, LP],
      rightPredValidator: PrimitiveValidatorForPredicate[N, RP],
      leftRefinedValidator: Validate[N, LP],
      rightRefinedValidator: Validate[N, RP]
  ): ValidatorForPredicate[N, LP Or RP] =
    new ValidatorForPredicate[N, LP Or RP] {
      override def validator: Validator[N] = Validator.any(leftPredValidator.validator, rightPredValidator.validator)
      override def validationErrors(value: N, refinedErrorMessage: String): List[ValidationError[_]] = {
        val primitivesErrors = Seq[(Validate[N, _], Validator.Primitive[N])](
          leftRefinedValidator -> leftPredValidator.validator,
          rightRefinedValidator -> rightPredValidator.validator
        )
          .filter { case (refinedValidator, _) => refinedValidator.notValid(value) }
          .map { case (_, primitiveValidator) => ValidationError.Primitive[N](primitiveValidator, value) }
          .toList

        if (primitivesErrors.isEmpty) {
          //this should not happen
          List(ValidationError.Custom(value, refinedErrorMessage, List()))
        } else {
          primitivesErrors
        }
      }
    }
}

trait ValidatorForPredicate[V, P] {
  def validator: Validator[V]
  def validationErrors(value: V, refinedErrorMessage: String): List[ValidationError[_]]
}

trait PrimitiveValidatorForPredicate[V, P] extends ValidatorForPredicate[V, P] {
  def validator: Validator.Primitive[V]
  def validationErrors(value: V, refinedErrorMessage: String): List[ValidationError[_]]
}

object ValidatorForPredicate {
  def fromPrimitiveValidator[V, P](primitiveValidator: Validator.Primitive[V]): PrimitiveValidatorForPredicate[V, P] =
    new PrimitiveValidatorForPredicate[V, P] {
      override def validator: Validator.Primitive[V] = primitiveValidator
      override def validationErrors(value: V, refinedErrorMessage: String): List[ValidationError[_]] =
        List(ValidationError.Primitive[V](primitiveValidator, value))
    }
}

trait LowPriorityValidatorForPredicate {
  implicit def genericValidatorForPredicate[V, P: ClassTag](implicit
      refinedValidator: Validate[V, P]
  ): ValidatorForPredicate[V, P] =
    new ValidatorForPredicate[V, P] {
      override val validator: Validator.Custom[V] = Validator.Custom(
        { v =>
          if (refinedValidator.isValid(v)) {
            List.empty
          } else {
            List(ValidationError.Custom(v, implicitly[ClassTag[P]].runtimeClass.toString))
          }
        }
      ) //for the moment there is no way to get a human description of a predicate/validator without having a concrete value to run it

      override def validationErrors(value: V, refinedErrorMessage: String): List[ValidationError[_]] =
        List(ValidationError.Custom[V](value, refinedErrorMessage))
    }
}
