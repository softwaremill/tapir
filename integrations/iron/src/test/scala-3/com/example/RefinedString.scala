package com.example

import io.github.iltotore.iron.*
import sttp.tapir.Validator
import sttp.tapir.codec.iron.PrimitiveValidatorForPredicate
import sttp.tapir.codec.iron.ValidatorForPredicate

final class RefinedStringConstraint

object RefinedStringConstraint {

  given Constraint[String, RefinedStringConstraint] with {

    override inline def test(inline value: String): Boolean = value.nonEmpty

    override inline def message: String = "Should not be empty"
  }

  given PrimitiveValidatorForPredicate[String, RefinedStringConstraint] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.pattern[String]("^.+"))
}

object RefinedString extends RefinedType[String, RefinedStringConstraint]
type RefinedString = RefinedString.T
