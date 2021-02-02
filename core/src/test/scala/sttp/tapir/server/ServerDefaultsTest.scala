package sttp.tapir.server

import sttp.tapir.{Schema, Validator}
import sttp.tapir.generic.auto._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ServerDefaultsTest extends AnyFlatSpec with Matchers {
  it should "create a validation error message for a nested field" in {
    // given
    implicit val addressNumberSchema: Schema[Int] = Schema.schemaForInt.validate(Validator.min(1))

    // when
    val validationErrors = implicitly[Schema[Person]].validator.validate(Person("John", Address("Lane", 0)))

    // then
    ServerDefaults.ValidationMessages.validationErrorsMessage(
      validationErrors
    ) shouldBe "expected address.number to be greater than or equal to 1, but was 0"
  }

  case class Person(name: String, address: Address)
  case class Address(street: String, number: Int)
}
