package sttp.tapir.server.interceptor.decodefailure

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.DecodeResult.Error.{JsonDecodeException, JsonError, MultipartDecodeException}
import sttp.tapir.generic.auto._
import sttp.tapir.{DecodeResult, FieldName, Schema, ValidationResult, Validator}

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
    ) shouldBe "expected value to be one of (1, 10, 11), but was 4"
  }

  it should "create a validation error message for custom validations" in {
    // given
    val numberSchema: Schema[Int] =
      Schema.schemaForInt.validate(Validator.custom[Int]((i: Int) => ValidationResult.validWhen(i == 2), Some("should be even")))

    // when
    val validationErrors = numberSchema.applyValidation(3)

    // then
    DefaultDecodeFailureHandler.ValidationMessages.validationErrorsMessage(
      validationErrors
    ) shouldBe "expected value to pass validation, should be even, but was: 3"
  }

  it should "create an error message including failed json paths" in {
    // given
    val error = JsonDecodeException(
      List(
        JsonError("error.path.missing", List(FieldName("obj"), FieldName("customer"), FieldName("yearOfBirth"))),
        JsonError("error.path.missing", List(FieldName("obj"), FieldName("items[0]"), FieldName("price")))
      ),
      new Exception("JsResultException")
    )

    // when
    val msg = DefaultDecodeFailureHandler.FailureMessages.failureDetailMessage(DecodeResult.Error("", error))

    // then
    msg shouldBe Some("error.path.missing at 'obj.customer.yearOfBirth', error.path.missing at 'obj.items[0].price'")
  }

  it should "create an error message including failed multipart parts" in {
    // given
    val error = MultipartDecodeException(List(("part1", DecodeResult.Missing)))

    // when
    val msg = DefaultDecodeFailureHandler.FailureMessages.failureDetailMessage(DecodeResult.Error("", error))

    // then
    msg shouldBe Some("part: part1 (missing)")
  }

  case class Person(name: String, address: Address)
  case class Address(street: String, number: Int)
}
