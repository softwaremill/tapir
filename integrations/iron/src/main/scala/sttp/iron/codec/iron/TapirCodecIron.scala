package sttp.tapir.codec.iron

import sttp.tapir.Schema
import io.github.iltotore.iron.Constraint
import io.github.iltotore.iron.:|
import io.github.iltotore.iron.refineEither
import io.github.iltotore.iron.refineOption
import io.github.iltotore.iron.constraint.any.*
import io.github.iltotore.iron.constraint.string.*
import io.github.iltotore.iron.constraint.collection.*
import io.github.iltotore.iron.constraint.numeric.*

import sttp.tapir.CodecFormat
import sttp.tapir.Codec
import sttp.tapir.DecodeResult
import sttp.tapir.Validator
import sttp.tapir.ValidationResult
import scala.reflect.ClassTag
import sttp.tapir.ValidationError
import io.github.iltotore.iron.constraint.any.Not

trait TapirCodecIron extends LowPriorityValidatorForPredicate {

  inline given ironTypeSchema[Value, Predicate](using
      inline vSchema: Schema[Value],
      inline constraint: Constraint[Value, Predicate],
      inline validatorTranslation: ValidatorForPredicate[Value, Predicate]
  ): Schema[Value :| Predicate] =
    vSchema.validate(validatorTranslation.validator).map[Value :| Predicate](v => v.refineOption[Predicate])(identity)

  inline given [Representation, Value, Predicate, CF <: CodecFormat](using
      inline tm: Codec[Representation, Value, CF],
      inline constraint: Constraint[Value, Predicate],
      inline validatorTranslation: ValidatorForPredicate[Value, Predicate]
  ): Codec[Representation, Value :| Predicate, CF] =
    summon[Codec[Representation, Value, CF]]
      .validate(validatorTranslation.validator)
      .mapDecode { (v: Value) =>
        v.refineEither[Predicate] match {
          case Right(refined) => DecodeResult.Value[Value :| Predicate](refined)
          case Left(errorMessage) =>
            DecodeResult.InvalidValue(validatorTranslation.makeErrors(v, constraint.message))
        }
      }(identity)

  inline given (using
      inline vSchema: Schema[String],
      inline refinedValidator: Constraint[String, ValidUUID],
      inline refinedValidatorTranslation: ValidatorForPredicate[String, ValidUUID]
  ): Schema[String :| ValidUUID] =
    ironTypeSchema[String, ValidUUID].format("uuid")

  inline given PrimitiveValidatorForPredicate[String, Not[Empty]] =
    ValidatorForPredicate.fromPrimitiveValidator[String, Not[Empty]](Validator.minLength(1))

  inline given validatorForMatchesRegexpString[S <: String](using witness: ValueOf[S]): PrimitiveValidatorForPredicate[String, Match[S]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.pattern[String](witness.value))

  inline given validatorForMaxSizeOnString[T <: String, NM <: Int](using
      witness: ValueOf[NM]
  ): PrimitiveValidatorForPredicate[T, MaxLength[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.maxLength[T](witness.value))

  inline given validatorForMinSizeOnString[T <: String, NM <: Int](using
      witness: ValueOf[NM]
  ): PrimitiveValidatorForPredicate[T, MinLength[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.minLength[T](witness.value))

  inline given validatorForLess[N: Numeric, NM <: N](using witness: ValueOf[NM]): PrimitiveValidatorForPredicate[N, Less[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(witness.value, exclusive = true))

  inline given validatorForLessEqual[N: Numeric, NM <: N](using witness: ValueOf[NM]): PrimitiveValidatorForPredicate[N, LessEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(witness.value, exclusive = false))

  inline given validatorForGreater[N: Numeric, NM <: N](using witness: ValueOf[NM]): PrimitiveValidatorForPredicate[N, Greater[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(witness.value, exclusive = true))

  inline given validatorForGreaterEqual[N: Numeric, NM <: N](using
      witness: ValueOf[NM]
  ): PrimitiveValidatorForPredicate[N, GreaterEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(witness.value))

  inline given validatorForAnd[N, LP, RP](using
      leftPredValidator: PrimitiveValidatorForPredicate[N, LP],
      rightPredValidator: PrimitiveValidatorForPredicate[N, RP],
      inline leftConstraint: Constraint[N, LP],
      inline rightConstraint: Constraint[N, RP]
  ): ValidatorForPredicate[N, DescribedAs[LP & RP, _]] = new ValidatorForPredicate[N, DescribedAs[LP & RP, _]] {
    override def validator: Validator[N] = Validator.all(leftPredValidator.validator, rightPredValidator.validator)
    override def makeErrors(value: N, errorMessage: String): List[ValidationError[_]] = {

      val leftErrors = if (!leftConstraint.test(value)) List(ValidationError[N](leftPredValidator.validator, value)) else List.empty

      val rightErrors = if (!rightConstraint.test(value)) List(ValidationError[N](rightPredValidator.validator, value)) else List.empty

      leftErrors ::: rightErrors

      // val primitivesErrors = Seq[(Constraint[N, _], Validator.Primitive[N])](
      //   leftConstraint -> leftPredValidator.validator,
      //   rightConstraint -> rightPredValidator.validator
      // )
      //   .filterNot { (constraint, _) => constraint.test(value) } // NOTE - tried this and it does not work, since we lost track of the constraint being inline
      //   .map { (_, primitiveValidator) => ValidationError[N](primitiveValidator, value) }
      //   .toList

      // if (primitivesErrors.isEmpty) {
      //   // this should not happen
      //   List(ValidationError(Validator.Custom((_: N) => ValidationResult.Valid), value, Nil, Some(errorMessage)))
      // } else {
      //   primitivesErrors
      // }
    }
  }

  inline given validatorForOr[N, LP, RP](using
      leftPredValidator: PrimitiveValidatorForPredicate[N, LP],
      rightPredValidator: PrimitiveValidatorForPredicate[N, RP],
      inline leftConstraint: Constraint[N, LP],
      inline rightConstraint: Constraint[N, RP]
  ): ValidatorForPredicate[N, LP | RP] =
    new ValidatorForPredicate[N, LP | RP] {
      override def validator: Validator[N] = Validator.any(leftPredValidator.validator, rightPredValidator.validator)
      override def makeErrors(value: N, errorMessage: String): List[ValidationError[_]] = {

        val leftErrors = if (!leftConstraint.test(value)) List(ValidationError[N](leftPredValidator.validator, value)) else List.empty

        val rightErrors = if (!rightConstraint.test(value)) List(ValidationError[N](rightPredValidator.validator, value)) else List.empty

        leftErrors ::: rightErrors

      }
    }
}

private[iron] trait ValidatorForPredicate[Value, Predicate] {
  def validator: Validator[Value]
  def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]]
}

private[iron] trait PrimitiveValidatorForPredicate[Value, Predicate] extends ValidatorForPredicate[Value, Predicate] {
  def validator: Validator.Primitive[Value]
  def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]]
}

private[iron] object ValidatorForPredicate {
  def fromPrimitiveValidator[Value, Predicate](
      primitiveValidator: Validator.Primitive[Value]
  ): PrimitiveValidatorForPredicate[Value, Predicate] =
    new PrimitiveValidatorForPredicate[Value, Predicate] {
      override def validator: Validator.Primitive[Value] = primitiveValidator
      override def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]] =
        List(ValidationError[Value](primitiveValidator, value))
    }
}

private[iron] trait LowPriorityValidatorForPredicate {
  inline given [Value, Predicate](using inline constraint: Constraint[Value, Predicate]): ValidatorForPredicate[Value, Predicate] =
    new ValidatorForPredicate[Value, Predicate] {

      override val validator: Validator.Custom[Value] = Validator.Custom { v =>
        if (constraint.test(v)) ValidationResult.Valid
        else ValidationResult.Invalid(constraint.message)
      }

      override def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]] =
        List(ValidationError[Value](validator, value, Nil, Some(errorMessage)))
    }
}
