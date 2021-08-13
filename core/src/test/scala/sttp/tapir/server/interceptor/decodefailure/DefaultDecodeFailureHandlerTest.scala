package sttp.tapir.server.interceptor.decodefailure

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.{Schema, ValidationResult, Validator}
import sttp.tapir.generic.auto._

class DefaultDecodeFailureHandlerTest extends AnyFlatSpec with Matchers {
  it should "create a validation error message for a nested field" in {
    // given
    implicit val addressNumberSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(1))

    // when
    val result: ValidationResult[Person] = implicitly[Schema[Person]].applyValidation(Person("John", Address("Lane", 0)))

    // then
    result.invalid.get.description shouldBe "expected address.number to be greater than or equal to 1, but was 0"
  }

  case class Person(name: String, address: Address)
  case class Address(street: String, number: Int)
}
