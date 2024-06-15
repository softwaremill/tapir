package sttp.tapir.codec.iron

import io.github.iltotore.iron.Constraint
import io.github.iltotore.iron.:|
import io.github.iltotore.iron.refineEither
import io.github.iltotore.iron.refineOption
import io.github.iltotore.iron.RefinedTypeOps
import io.github.iltotore.iron.constraint.any.*
import io.github.iltotore.iron.constraint.string.*
import io.github.iltotore.iron.constraint.collection.*
import io.github.iltotore.iron.constraint.numeric.*

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.DecodeResult
import sttp.tapir.internal.*
import sttp.tapir.Schema
import sttp.tapir.Validator
import sttp.tapir.Validator.Primitive
import sttp.tapir.ValidationError
import sttp.tapir.ValidationResult
import sttp.tapir.typelevel.IntersectionTypeMirror
import sttp.tapir.typelevel.UnionTypeMirror

import scala.compiletime.*
import scala.util.NotGiven

trait TapirCodecIron extends DescriptionWitness with LowPriorityValidatorForPredicate {

  inline given ironTypeSchema[Value, Predicate](using
      inline vSchema: Schema[Value],
      inline constraint: Constraint[Value, Predicate],
      inline validatorTranslation: ValidatorForPredicate[Value, Predicate]
  ): Schema[Value :| Predicate] =
    vSchema.validate(validatorTranslation.validator).map[Value :| Predicate](v => v.refineOption[Predicate])(identity)

  inline given ironTypeSchemaIterable[X, C[X] <: Iterable[X], Predicate](using
      inline vSchema: Schema[C[X]],
      inline constraint: Constraint[C[X], Predicate],
      inline validatorTranslation: ValidatorForPredicate[C[X], Predicate]
  ): Schema[C[X] :| Predicate] =
    vSchema
      .validate(validatorTranslation.validator)
      .map[C[X] :| Predicate](v => v.refineOption[Predicate])(identity)
      .copy(isOptional = if (validatorTranslation.containsMinSizePositive) false else vSchema.isOptional)

  inline given ironTypeCodec[Representation, Value, Predicate, CF <: CodecFormat](using
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

  given refinedTypeSchema[T](using m: RefinedTypeOps.Mirror[T], ev: Schema[m.IronType]): Schema[T] =
    ev.asInstanceOf[Schema[T]]

  given refinedTypeCodec[R, T, CF <: CodecFormat](using m: RefinedTypeOps.Mirror[T], ev: Codec[R, m.IronType, CF]): Codec[R, T, CF] =
    ev.asInstanceOf[Codec[R, T, CF]]

  inline given (using
      inline vSchema: Schema[String],
      inline constraint: Constraint[String, ValidUUID],
      inline validatorTranslation: ValidatorForPredicate[String, ValidUUID]
  ): Schema[String :| ValidUUID] =
    ironTypeSchema[String, ValidUUID].format("uuid")

  inline given PrimitiveValidatorForPredicate[String, Not[Empty]] =
    ValidatorForPredicate.fromPrimitiveValidator[String, Not[Empty]](Validator.minLength(1))

  inline given validatorForPure[T]: ValidatorForPredicate[T, Pure] =
    new ValidatorForPredicate[T, Pure] {
      override val validator: Validator[T] = Validator.pass
      override def makeErrors(value: T, errorMessage: String): List[ValidationError[_]] = Nil
    }

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

  inline given validatorForMinLengthOnIterable[X, C[X] <: Iterable[X], NM <: Int](using
      witness: ValueOf[NM]
  ): PrimitiveValidatorForPredicate[C[X], MinLength[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.minSize[X, C](witness.value))
  
  inline given validatorForNonEmptyIterable[X, C[X] <: Iterable[X], NM <: Int]: PrimitiveValidatorForPredicate[C[X], Not[Empty]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.minSize[X, C](1))
  
  inline given validatorForMaxLengthOnIterable[X, C[X] <: Iterable[X], NM <: Int](using
      witness: ValueOf[NM]
  ): PrimitiveValidatorForPredicate[C[X], MaxLength[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.maxSize[X, C](witness.value))

  inline given validatorForLess[N: Numeric, NM <: N](using witness: ValueOf[NM]): PrimitiveValidatorForPredicate[N, Less[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(witness.value, exclusive = true))

  inline given validatorForLessEqual[N: Numeric, NM <: N](using witness: ValueOf[NM]): PrimitiveValidatorForPredicate[N, LessEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.max(witness.value, exclusive = false))

  inline given validatorForGreater[N: Numeric, NM <: N](using witness: ValueOf[NM]): PrimitiveValidatorForPredicate[N, Greater[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(witness.value, exclusive = true))

  inline given validatorForGreaterEqual[N: Numeric, NM <: N](using
      witness: ValueOf[NM]
  ): PrimitiveValidatorForPredicate[N, GreaterEqual[NM]] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.min(witness.value, exclusive = false))

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
        val headValidator: ValidatorForPredicate[N, ?] = summonFrom {
          case pv: PrimitiveValidatorForPredicate[N, head] => pv
          case _ => summonInline[ValidatorForPredicate[N, head]]
        }
        headValidator.asInstanceOf[ValidatorForPredicate[N, Any]] :: summonValidators[N, tail]
  }

  inline given validatorForAnd[N, Predicates](using mirror: IntersectionTypeMirror[Predicates]): ValidatorForPredicate[N, Predicates] =
    new ValidatorForPredicate[N, Predicates] {

      val intersectionConstraint = new Constraint.IntersectionConstraint[N, Predicates]
      val validatorsForPredicates: List[ValidatorForPredicate[N, Any]] = summonValidators[N, mirror.ElementTypes]

      override def validator: Validator[N] = Validator.all(validatorsForPredicates.map(_.validator): _*)

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

  private inline def strictValues[N, V <: Tuple]: List[N] = {
    inline erasedValue[V] match
      case _: EmptyTuple => Nil
      case _: (StrictEqual[t] *: ts) =>
        inline erasedValue[t] match
          case e: N => e :: strictValues[N, ts]
          case _    => Nil
      case _ => Nil
  }

  inline given validatorForOr[N, Predicates](using
      mirror: UnionTypeMirror[Predicates]
  ): ValidatorForPredicate[N, Predicates] =
    val strictEqualsValues = strictValues[N, mirror.ElementTypes]
    if (strictEqualsValues.length == mirror.size)
      // All elements of union type were StrictEqual[_], so it's simply an enumeration
      ValidatorForPredicate.fromPrimitiveValidator(Validator.enumeration[N](strictEqualsValues))
    else
      new ValidatorForPredicate[N, Predicates] {

        val unionConstraint = new Constraint.UnionConstraint[N, Predicates]
        val validatorsForPredicates: List[ValidatorForPredicate[N, Any]] =
          if strictEqualsValues.isEmpty then summonValidators[N, mirror.ElementTypes]
          else
            // There were some strict equals at the beginning of union type - putting them into a Validator.enumeration and attaching the rest of the validators as a normal list
            ValidatorForPredicate
              .fromPrimitiveValidator(Validator.enumeration[N](strictEqualsValues)) :: summonValidators[N, mirror.ElementTypes].drop(
              strictEqualsValues.length
            )

        override def validator: Validator[N] = Validator.any(validatorsForPredicates.map(_.validator): _*)

        override def makeErrors(value: N, errorMessage: String): List[ValidationError[_]] =
          if (!unionConstraint.test(value))
            List(
              ValidationError[N](
                Validator.Custom(_ =>
                  ValidationResult.Invalid(unionConstraint.message) // at this point the validator is already failed anyway
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

  inline given validatorForDescribedOr[N, P, Num](using
      id: IsDescription[P],
      mirror: UnionTypeMirror[id.Predicate],
      notGe: NotGiven[P =:= GreaterEqual[Num]],
      notLe: NotGiven[P =:= LessEqual[Num]]
  ): ValidatorForPredicate[N, P] =
    validatorForOr[N, id.Predicate].asInstanceOf[ValidatorForPredicate[N, P]]

  inline given validatorForDescribedOrGe[N: Numeric, P, Num <: N](using
      id: IsDescription[P],
      isGe: P =:= GreaterEqual[Num],
      singleton: ValueOf[Num]
  ): ValidatorForPredicate[N, P] =
    validatorForGreaterEqual[N, Num].asInstanceOf[ValidatorForPredicate[N, P]]

  inline given validatorForDescribedOrLe[N: Numeric, P, Num <: N](using
      id: IsDescription[P],
      isLe: P =:= LessEqual[Num],
      singleton: ValueOf[Num]
  ): ValidatorForPredicate[N, P] =
    validatorForLessEqual[N, Num].asInstanceOf[ValidatorForPredicate[N, P]]
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
  lazy val containsMinSizePositive: Boolean = validator.asPrimitiveValidators.exists {
    case Validator.MinSize(a) => a > 0
    case _                    => false
  }
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
