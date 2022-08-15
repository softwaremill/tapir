package sttp.tapir

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidatorTest extends AnyFlatSpec with Matchers {
  it should "validate for min value" in {
    val min = 1
    val wrong = 0
    val v = Validator.min(min)
    v(wrong) shouldBe List(ValidationError(v, wrong))
    v(min) shouldBe empty
  }

  it should "validate for min value (exclusive)" in {
    val min = 1
    val wrong = 0
    val v = Validator.min(min, exclusive = true)
    v(wrong) shouldBe List(ValidationError(v, wrong))
    v(min) shouldBe List(ValidationError(v, min))
    v(min + 1) shouldBe empty
  }

  it should "validate for max value" in {
    val max = 0
    val wrong = 1
    val v = Validator.max(max)
    v(wrong) shouldBe List(ValidationError(v, wrong))
    v(max) shouldBe empty
  }

  it should "validate for max value (exclusive)" in {
    val max = 0
    val wrong = 1
    val v = Validator.max(max, exclusive = true)
    v(wrong) shouldBe List(ValidationError(v, wrong))
    v(max) shouldBe List(ValidationError(v, max))
    v(max - 1) shouldBe empty
  }

  it should "validate for positive value" in {
    val value = 1
    val wrongNegative = -1
    val wrongZero = 0
    val v = Validator.positive[Int]
    v(wrongNegative) shouldBe List(ValidationError[Int](v, wrongNegative))
    v(wrongZero) shouldBe List(ValidationError[Int](v, wrongZero))
    v(value) shouldBe empty
  }

  it should "validate for positiveOrZero value" in {
    val wrongNegative = -1
    val positiveValue = 1
    val zeroValue = 0
    val v = Validator.positiveOrZero[Int]
    v(wrongNegative) shouldBe List(ValidationError[Int](v, wrongNegative))
    v(zeroValue) shouldBe empty
    v(positiveValue) shouldBe empty
  }

  it should "validate for negative value" in {
    val value = -1
    val wrongPositive = 1
    val wrongZero = 0
    val v = Validator.negative[Int]
    v(wrongPositive) shouldBe List(ValidationError(v, wrongPositive))
    v(wrongZero) shouldBe List(ValidationError(v, wrongZero))
    v(value) shouldBe empty
  }

  it should "validate for in range value" in {
    val min = 0
    val max = 1
    val value1 = 0
    val value2 = 1
    val wrongMaxOut = 2
    val wrongMinOut = -1
    val v = Validator.inRange(min, max)

    v(wrongMaxOut) shouldBe List(ValidationError(Validator.max(max), wrongMaxOut))
    v(wrongMinOut) shouldBe List(ValidationError(Validator.min(min), wrongMinOut))
    v(value1) shouldBe empty
    v(value2) shouldBe empty
  }

  it should "validate for maxSize of collection" in {
    val expected = 1
    val actual = List(1, 2, 3)
    val v = Validator.maxSize[Int, List](expected)
    v(actual) shouldBe List(ValidationError(v, actual))
    v(List(1)) shouldBe empty
  }

  it should "validate for minSize of collection" in {
    val expected = 3
    val v = Validator.minSize[Int, List](expected)
    v(List(1, 2)) shouldBe List(ValidationError(v, List(1, 2)))
    v(List(1, 2, 3)) shouldBe empty
  }

  it should "validate for nonEmpty of collection" in {
    val v = Validator.nonEmpty[Int, List]
    v(List()) shouldBe List(ValidationError[List[Int]](Validator.minSize[Int, List](1), List()))
    v(List(1)) shouldBe empty
  }

  it should "validate for fixedSize of collection" in {
    val v = Validator.fixedSize[Int, List](3)
    v(List(1, 2)) shouldBe List(ValidationError(Validator.minSize[Int, List](3), List(1, 2)))
    v(List(1, 2, 3, 4)) shouldBe List(ValidationError(Validator.maxSize[Int, List](3), List(1, 2, 3, 4)))
    v(List(1, 2, 3)) shouldBe empty
  }

  it should "validate for matching regex pattern" in {
    val expected = "^apple$|^banana$"
    val wrong = "orange"
    Validator.pattern(expected)(wrong) shouldBe List(ValidationError(Validator.pattern(expected), wrong))
    Validator.pattern(expected)("banana") shouldBe empty
  }

  it should "validate for minLength of string" in {
    val expected = 3
    val v = Validator.minLength[String](expected)
    v("ab") shouldBe List(ValidationError(v, "ab"))
    v("abc") shouldBe empty
  }

  it should "validate for maxLength of string" in {
    val expected = 1
    val v = Validator.maxLength[String](expected)
    v("ab") shouldBe List(ValidationError(v, "ab"))
    v("a") shouldBe empty
  }

  it should "validate for fixedLength of string" in {
    val v = Validator.fixedLength[String](3)
    v("ab") shouldBe List(ValidationError(Validator.minLength(3), "ab"))
    v("abcd") shouldBe List(ValidationError(Validator.maxLength(3), "abcd"))
    v("abc") shouldBe empty
  }

  it should "validate for nonEmptyString of string" in {
    val v = Validator.nonEmptyString[String]
    v("") shouldBe List(ValidationError(Validator.minLength(1), ""))
    v("abc") shouldBe empty
  }

  it should "validate with reject" in {
    val validator = Validator.reject[Int]
    validator(4) shouldBe List(ValidationError(Validator.Any.EmptyValidators, 4))
    validator(7) shouldBe List(ValidationError(Validator.Any.EmptyValidators, 7))
    validator(11) shouldBe List(ValidationError(Validator.Any.EmptyValidators, 11))
  }

  it should "validate with any of validators" in {
    val validator = Validator.any(Validator.max(5), Validator.max(10))
    validator(4) shouldBe empty
    validator(7) shouldBe empty
    validator(11) shouldBe List(
      ValidationError(Validator.max(5), 11),
      ValidationError(Validator.max(10), 11)
    )
  }

  it should "validate with all of validators" in {
    val validator = Validator.all(Validator.min(3), Validator.max(10))
    validator(4) shouldBe empty
    validator(2) shouldBe List(ValidationError(Validator.min(3), 2))
    validator(11) shouldBe List(ValidationError(Validator.max(10), 11))
  }

  it should "validate with custom validator" in {
    val v = Validator.custom(
      { (x: Int) =>
        if (x > 5) ValidationResult.Valid else ValidationResult.Invalid("X has to be greater than 5!")
      }
    )
    v(0) should matchPattern { case List(ValidationError(_, 0, Nil, Some("X has to be greater than 5!"))) => }
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
    val v = Validator.enumeration(List(1, 2, 3, 4))
    v.apply(1) shouldBe empty
    v.apply(0) shouldBe List(ValidationError(v, 0))
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
