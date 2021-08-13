package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.tapir.ValidationResult.{Invalid, Valid}
import sttp.tapir.testing.ValidationResultMatchers

class ValidatorTest extends AnyFlatSpec with Matchers with ValidationResultMatchers {
  it should "validate for min value" in {
    val validator = Validator.min[Int](1)

    validator(1) shouldBe valid(1)
    validator(0) shouldBe invalid(0)
  }

  it should "validate for min value (exclusive)" in {
    val validator = Validator.min[Int](1, exclusive = true)

    validator(2) shouldBe valid(2)
    validator(0) shouldBe invalid(0)
    validator(1) shouldBe invalid(1)
  }

  it should "validate for max value" in {
    val validator = Validator.max[Int](0)

    validator(0) shouldBe valid(0)
    validator(1) shouldBe invalid(1)
  }

  it should "validate for max value (exclusive)" in {
    val validator = Validator.max[Int](0, exclusive = true)

    validator(-1) shouldBe valid(-1)
    validator(0) shouldBe invalid(0)
    validator(1) shouldBe invalid(1)
  }

  it should "validate for maxSize of collection" in {
    val validator = Validator.maxSize[Int, List](1)

    validator(List(1)) shouldBe valid(List(1))
    validator(List(1, 2, 3)) shouldBe invalid(List(1, 2, 3))
  }

  it should "validate for minSize of collection" in {
    val validator = Validator.minSize[Int, List](3)

    validator(List(1, 2, 3)) shouldBe valid(List(1, 2, 3))
    validator(List(1, 2)) shouldBe invalid(List(1, 2))
  }

  it should "validate for matching regex pattern" in {
    val validator = Validator.pattern[String]("^apple$|^banana$")

    validator("banana") shouldBe valid("banana")
    validator("orange") shouldBe invalid("orange")
  }

  it should "validate for minLength of string" in {
    val validator = Validator.minLength[String](3)

    validator("abc") shouldBe valid("abc")
    validator("ab") shouldBe invalid("ab")
  }

  it should "validate for maxLength of string" in {
    val validator = Validator.maxLength[String](1)

    validator("a") shouldBe valid("a")
    validator("ab") shouldBe invalid("ab")
  }

  it should "validate with any of validators" in {
    val validator = Validator.any(Validator.max(5), Validator.max(10))

    validator(4) shouldBe valid(4)
    validator(7) shouldBe valid(7)
    validator(11) shouldBe invalidWithMultipleErrors(11, expectedErrors = 2)
  }

  it should "validate with all of validators" in {
    val validator = Validator.all(Validator.min(3), Validator.max(10))

    validator(4) shouldBe valid(4)
    validator(2) shouldBe invalid(2)
    validator(11) shouldBe invalid(11)
  }

  it should "validate with custom validator" in {
    val validator = Validator.custom(
      { (x: Int) =>
        if (x > 5)
          Valid(x)
        else
          Invalid(
            value = x,
            errors = List(ValidationError.expectedTo(to = "be greater than 5", butWas = x))
          )
      }
    )

    validator(0) shouldBe invalid(0)
  }

  it should "validate coproduct enum" in {
    Validator.derivedEnumeration[Color].possibleValues should contain theSameElementsAs List(Blue, Red)
  }

  it should "not compile for malformed coproduct enum" in {
    assertDoesNotCompile("""
      Validator.derivedEnumeration[InvalidColorEnum]
    """)
  }

  it should "validate closed set of ints" in {
    val validator = Validator.enumeration(List(1, 2, 3, 4))

    validator(1) shouldBe valid(1)
    validator(0) shouldBe invalid(0)
  }
}

sealed trait Color
case object Blue extends Color
case object Red extends Color

sealed trait InvalidColorEnum
object InvalidColorEnum {
  case object Blue extends InvalidColorEnum
  case class Red(s: String) extends InvalidColorEnum
}
