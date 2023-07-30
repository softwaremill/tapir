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
import io.github.iltotore.iron.macros.union.IsUnion
import io.github.iltotore.iron.macros.intersection.IsIntersection

import scala.compiletime.*
import scala.util.NotGiven

import sttp.tapir.typelevel.IntersectionTypeMirror
import sttp.tapir.Validator.Primitive
import sttp.tapir.typelevel.UnionTypeMirror

trait TapirCodecIron extends DescriptionWitness with LowPriorityValidatorForPredicate {

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
      inline constraint: Constraint[String, ValidUUID],
      inline validatorTranslation: ValidatorForPredicate[String, ValidUUID]
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

  inline given validatorForStrictEqual[N: Numeric, NM <: N](using
      witness: ValueOf[NM]
  ): PrimitiveValidatorForPredicate[N, StrictEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(
      Validator.enumeration[N](List(witness.value))
    )

  private inline def summonValidators[N, A <: Tuple]: List[ValidatorForPredicate[N, Any]] = {
    inline erasedValue[A] match
      case _: EmptyTuple => Nil
      case _: (head *: tail) =>
        summonInline[ValidatorForPredicate[N, head]]
          .asInstanceOf[ValidatorForPredicate[N, Any]] :: summonValidators[N, tail]
  }

  private inline def summonConstraints[N, A <: Tuple]: List[Constraint[N, Any]] = {
    inline erasedValue[A] match
      case _: EmptyTuple     => Nil
      case _: (head *: tail) => summonInline[Constraint[N, head]].asInstanceOf[Constraint[N, Any]] :: summonConstraints[N, tail]
  }

  inline given validatorForAnd[N, Predicates](using mirror: IntersectionTypeMirror[Predicates]): ValidatorForPredicate[N, Predicates] =
    new ValidatorForPredicate[N, Predicates] {

      val intersectionConstraint = new Constraint.IntersectionConstraint[N, Predicates]

      val validatorsForPredicates: List[ValidatorForPredicate[N, Any]] = summonValidators[N, mirror.ElementTypes]

      val primitiveValidators = validatorsForPredicates.map(_.validator)

      override def validator: Validator[N] = Validator.all(primitiveValidators: _*)

      override def makeErrors(value: N, errorMessage: String): List[ValidationError[_]] =
        if (!intersectionConstraint.test(value))
          List(
            ValidationError[N](
              Validator.Custom(_ =>
                ValidationResult.Invalid(intersectionConstraint.message) // at this point the validator is already failed anyway
              ),
              value
            )
          )
        else Nil

    }

  inline given validatorForOr[N, Predicates](using mirror: UnionTypeMirror[Predicates]): ValidatorForPredicate[N, Predicates] =
    new ValidatorForPredicate[N, Predicates] {

      val intersectionConstraint = new Constraint.UnionConstraint[N, Predicates]

      val validatorsForPredicates: List[ValidatorForPredicate[N, Any]] = summonValidators[N, mirror.ElementTypes]

      val primitiveValidators = validatorsForPredicates.map(_.validator)

      override def validator: Validator[N] = Validator.any(primitiveValidators: _*)

      override def makeErrors(value: N, errorMessage: String): List[ValidationError[_]] =
        if (!intersectionConstraint.test(value))
          List(
            ValidationError[N](
              Validator.Custom(_ =>
                ValidationResult.Invalid(intersectionConstraint.message) // at this point the validator is already failed anyway
              ),
              value
            )
          )
        else Nil

    }

  inline given validatorForDescribedAnd[N, P](using
      id: IsDescription[P],
      mirror: IntersectionTypeMirror[id.Predicate]
  ): ValidatorForPredicate[N, P] =
    validatorForAnd[N, id.Predicate].asInstanceOf[ValidatorForPredicate[N, P]]

  inline given validatorForDescribedOr[N, P](using
      id: IsDescription[P],
      mirror: UnionTypeMirror[id.Predicate]
  ): ValidatorForPredicate[N, P] =
    validatorForOr[N, id.Predicate].asInstanceOf[ValidatorForPredicate[N, P]]

  inline given validatorForDescribedPrimitive[N, P](using
      id: IsDescription[P],
      notUnion: NotGiven[UnionTypeMirror[id.Predicate]],
      notIntersection: NotGiven[IntersectionTypeMirror[id.Predicate]],
      inline validator: ValidatorForPredicate[N, id.Predicate]
  ): ValidatorForPredicate[N, P] =
    validator.asInstanceOf[ValidatorForPredicate[N, P]]

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

  inline given [Value, Predicate](using
      inline constraint: Constraint[Value, Predicate],
      id: IsDescription[Predicate],
      notIntersection: NotGiven[IntersectionTypeMirror[id.Predicate]],
      notUnion: NotGiven[UnionTypeMirror[id.Predicate]]
  ): ValidatorForPredicate[Value, Predicate] =
    new ValidatorForPredicate[Value, Predicate] {

      override val validator: Validator.Custom[Value] = Validator.Custom { v =>
        if (constraint.test(v)) ValidationResult.Valid
        else ValidationResult.Invalid(constraint.message)
      }

      override def makeErrors(value: Value, errorMessage: String): List[ValidationError[_]] =
        List(ValidationError[Value](validator, value, Nil, Some(errorMessage)))
    }
}

private[iron] trait DescriptionWitness {

  trait IsDescription[A] {
    type Predicate
    type Description
  }

  class DescriptionTypeMirrorImpl[A, P, D <: String] extends IsDescription[A] {
    override type Predicate = P
    override type Description = D
  }
  object IsDescription {
    transparent inline given derived[A]: IsDescription[A] =
      inline erasedValue[A] match {
        case _: DescribedAs[p, d] => new DescriptionTypeMirrorImpl[A, p, d]
      }
  }

}
