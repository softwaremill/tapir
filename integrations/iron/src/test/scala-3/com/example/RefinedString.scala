package com.example

import io.github.iltotore.iron.*
import sttp.tapir.Validator
import sttp.tapir.codec.iron.PrimitiveValidatorForPredicate
import sttp.tapir.codec.iron.ValidatorForPredicate

final class RefinedStringConstraint

object RefinedStringConstraint {

  given Constraint[String, RefinedStringConstraint] with {

    override inline def test(value: String): Boolean = value.nonEmpty

    override inline def message: String = "Should not be empty"
  }

  given PrimitiveValidatorForPredicate[String, RefinedStringConstraint] =
    ValidatorForPredicate.fromPrimitiveValidator(Validator.pattern[String]("^.+"))
}

opaque type RefinedString = String :| RefinedStringConstraint

object RefinedString extends RefinedTypeOps[String, RefinedStringConstraint, RefinedString]
