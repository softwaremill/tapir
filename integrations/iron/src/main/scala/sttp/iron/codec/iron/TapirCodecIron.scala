package sttp.tapir.codec.iron

import sttp.tapir.Schema
import io.github.iltotore.iron.Constraint
import io.github.iltotore.iron.:|
import io.github.iltotore.iron.refineEither
import io.github.iltotore.iron.refineOption
import sttp.tapir.CodecFormat
import sttp.tapir.Codec
import sttp.tapir.DecodeResult
import sttp.tapir.Validator
import sttp.tapir.ValidationResult
import scala.reflect.ClassTag
import sttp.tapir.ValidationError

trait TapirCodecIron extends LowPriorityValidatorForPredicate {

  inline given [Value, Predicate](using
      inline vSchema: Schema[Value],
      inline constraint: Constraint[Value, Predicate],
      inline validatorTranslation: ValidatorForPredicate[Value, Predicate]
  ): Schema[Value :| Predicate] =
    vSchema.validate(validatorTranslation.validator).map[Value :| Predicate](v => v.refineOption[Predicate])(identity)

  inline given [Representation, Value, Predicate, CF <: CodecFormat](using
      inline tm: Codec[Representation, Value, CF],
      inline constraint: Constraint[Value, Predicate],
      inline validatorTranslation: ValidatorForPredicate[Value, Predicate]
  ): Codec[Representation, Value :| Predicate, CF] = {

    summon[Codec[Representation, Value, CF]]
      .validate(validatorTranslation.validator)
      .mapDecode { (v: Value) =>
        v.refineEither[Predicate] match {
          case Right(refined) => DecodeResult.Value[Value :| Predicate](refined)
          case Left(errorMessage) =>
            DecodeResult.InvalidValue(validatorTranslation.makeErrors(v, constraint.message))
        }
      }(identity)
  }
}

trait ValidatorForPredicate[Value, Predicate] {
  def validator: Validator[Value]
  def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]]
}

trait PrimitiveValidatorForPredicate[Value, Predicate] extends ValidatorForPredicate[Value, Predicate] {
  def validator: Validator.Primitive[Value]
  def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]]
}

object ValidatorForPredicate {
  def fromPrimitiveValidator[Value, Predicate](
      primitiveValidator: Validator.Primitive[Value]
  ): PrimitiveValidatorForPredicate[Value, Predicate] =
    new PrimitiveValidatorForPredicate[Value, Predicate] {
      override def validator: Validator.Primitive[Value] = primitiveValidator
      override def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]] =
        List(ValidationError[Value](primitiveValidator, value))
    }
}

trait LowPriorityValidatorForPredicate {
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
