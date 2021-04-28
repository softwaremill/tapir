package sttp.tapir

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.Duration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ValidatorTest extends AnyFlatSpec with Matchers {
  it should "validate for min value" in {
    val min = 1
    val wrong = 0
    val v = Validator.min(min)
    v(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v(min) shouldBe empty
  }

  it should "validate for min value (exclusive)" in {
    val min = 1
    val wrong = 0
    val v = Validator.min(min, exclusive = true)
    v(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v(min) shouldBe List(ValidationError.Primitive(v, min))
    v(min + 1) shouldBe empty
  }

  it should "validate for max value" in {
    val max = 0
    val wrong = 1
    val v = Validator.max(max)
    v(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v(max) shouldBe empty
  }

  it should "validate for max value (exclusive)" in {
    val max = 0
    val wrong = 1
    val v = Validator.max(max, exclusive = true)
    v(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v(max) shouldBe List(ValidationError.Primitive(v, max))
    v(max - 1) shouldBe empty
  }

  it should "validate for maxSize of collection" in {
    val expected = 1
    val actual = List(1, 2, 3)
    val v = Validator.maxSize[Int, List](expected)
    v(actual) shouldBe List(ValidationError.Primitive(v, actual))
    v(List(1)) shouldBe empty
  }

  it should "validate for minSize of collection" in {
    val expected = 3
    val v = Validator.minSize[Int, List](expected)
    v(List(1, 2)) shouldBe List(ValidationError.Primitive(v, List(1, 2)))
    v(List(1, 2, 3)) shouldBe empty
  }

  it should "validate for matching regex pattern" in {
    val expected = "^apple$|^banana$"
    val wrong = "orange"
    Validator.pattern(expected)(wrong) shouldBe List(ValidationError.Primitive(Validator.pattern(expected), wrong))
    Validator.pattern(expected)("banana") shouldBe empty
  }

  it should "validate for minLength of string" in {
    val expected = 3
    val v = Validator.minLength[String](expected)
    v("ab") shouldBe List(ValidationError.Primitive(v, "ab"))
    v("abc") shouldBe empty
  }

  it should "validate for maxLength of string" in {
    val expected = 1
    val v = Validator.maxLength[String](expected)
    v("ab") shouldBe List(ValidationError.Primitive(v, "ab"))
    v("a") shouldBe empty
  }

  it should "validate with any of validators" in {
    val validator = Validator.any(Validator.max(5), Validator.max(10))
    validator(4) shouldBe empty
    validator(7) shouldBe empty
    validator(11) shouldBe List(
      ValidationError.Primitive(Validator.max(5), 11),
      ValidationError.Primitive(Validator.max(10), 11)
    )
  }

  it should "validate with all of validators" in {
    val validator = Validator.all(Validator.min(3), Validator.max(10))
    validator(4) shouldBe empty
    validator(2) shouldBe List(ValidationError.Primitive(Validator.min(3), 2))
    validator(11) shouldBe List(ValidationError.Primitive(Validator.max(10), 11))
  }

  it should "validate with custom validator" in {
    val v = Validator.custom(
      { (x: Int) =>
        if (x > 5) {
          List.empty
        } else {
          List(ValidationError.Custom(x, "X has to be greater than 5!"))
        }
      }
    )
    v(0) shouldBe List(ValidationError.Custom(0, "X has to be greater than 5!"))
  }

  it should "validate enum" in {
    Validator.derivedEnumeration[Color](Blue) shouldBe empty
  }

  it should "validate closed set of ints" in {
    val v = Validator.enumeration(List(1, 2, 3, 4))
    v.apply(1) shouldBe empty
    v.apply(0) shouldBe List(ValidationError.Primitive(v, 0))
  }
}

sealed trait Color
case object Blue extends Color
case object Red extends Color
