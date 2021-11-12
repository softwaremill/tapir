package sttp.tapir.server.interceptor.decodefailure

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.generic.auto._
import sttp.tapir.{Schema, Validator}

class DefaultDecodeFailureHandlerTest extends AnyFlatSpec with Matchers {
  it should "create a validation error message for a nested field" in {
    // given
    implicit val addressNumberSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(1))

    // when
    val validationErrors = implicitly[Schema[Person]].applyValidation(Person("John", Address("Lane", 0)))

    // then
    DefaultDecodeFailureHandler.ValidationMessages.validationErrorsMessage(
      validationErrors
    ) shouldBe "expected address.number to be greater than or equal to 1, but was 0"
  }

  it should "create a validation error message including encoded enumeration values" in {
    // given
    val numberSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.enumeration(List(1, 2, 3)).encode(_.toBinaryString))

    // when
    val validationErrors = numberSchema.applyValidation(4)

    // then
    DefaultDecodeFailureHandler.ValidationMessages.validationErrorsMessage(
      validationErrors
    ) shouldBe "expected value to be within List(1, 10, 11), but was '4'"
  }

  case class Person(name: String, address: Address)
  case class Address(street: String, number: Int)
}
