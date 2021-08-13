package sttp.tapir.testing

import org.scalatest.matchers.{BeMatcher, MatchResult}
import sttp.tapir.ValidationResult.{Invalid, Valid}
import sttp.tapir.ValidationResult

object ValidationResultMatchers extends ValidationResultMatchers
trait ValidationResultMatchers {

  case object valid extends BeMatcher[ValidationResult[Any]] {
    override def apply(actual: ValidationResult[Any]): MatchResult = {
      MatchResult(
        actual.isInstanceOf[Valid[Any]],
        rawFailureMessage = s"Validation result expected to be Valid but got $actual!",
        rawNegatedFailureMessage = s"Validation result expected to be Invalid but got $actual!"
      )
    }
  }

  case class valid[T](expectedValue: T) extends BeMatcher[ValidationResult[T]] {
    override def apply(actual: ValidationResult[T]): MatchResult = {
      MatchResult(
        actual.valid.get.value == expectedValue,
        rawFailureMessage = s"Validation result expected to be Valid($expectedValue) but got $actual!",
        rawNegatedFailureMessage = s"Validation result expected to be Invalid($expectedValue) but got $actual!"
      )
    }
  }

  case object invalid extends BeMatcher[ValidationResult[Any]] {
    override def apply(actual: ValidationResult[Any]): MatchResult = {
      MatchResult(
        actual.isInstanceOf[Invalid[Any]],
        rawFailureMessage = s"Validation result expected to be Invalid but got $actual!",
        rawNegatedFailureMessage = s"Validation result expected to be Valid but got $actual!"
      )
    }
  }

  case class invalid[T](expectedValue: T) extends BeMatcher[ValidationResult[T]] {
    override def apply(actual: ValidationResult[T]): MatchResult = {
      MatchResult(
        actual.isInstanceOf[Invalid[T]] && actual.invalid.get.value == expectedValue,
        rawFailureMessage = s"Validation result expected to be Invalid($expectedValue) but got $actual!",
        rawNegatedFailureMessage = s"Validation result expected to be Valid($expectedValue) but got $actual!"
      )
    }
  }

  case class invalidWithMultipleErrors[T](expectedValue: T, expectedErrors: Int) extends BeMatcher[ValidationResult[T]] {
    override def apply(actual: ValidationResult[T]): MatchResult = {
      MatchResult(
        actual.isInstanceOf[Invalid[T]]
          && actual.invalid.get.value == expectedValue
          && actual.asInstanceOf[Invalid[T]].errors.size.equals(expectedErrors),
        rawFailureMessage = s"Validation result expected to be Invalid($expectedValue) with $expectedErrors errors but got $actual!",
        rawNegatedFailureMessage = s"Validation result expected to be Valid($expectedValue) but got $actual!"
      )
    }
  }
}
