package sttp.tapir

import java.util.concurrent.TimeUnit

import com.github.ghik.silencer.silent

import scala.concurrent.duration.Duration
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

@silent("never used")
class ValidatorTest extends AnyFlatSpec with Matchers {
  it should "validate for min value" in {
    val min = 1
    val wrong = 0
    val v = Validator.min(min)
    v.validate(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v.validate(min) shouldBe empty
  }

  it should "validate for min value (exclusive)" in {
    val min = 1
    val wrong = 0
    val v = Validator.min(min, exclusive = true)
    v.validate(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v.validate(min) shouldBe List(ValidationError.Primitive(v, min))
    v.validate(min + 1) shouldBe empty
  }

  it should "validate for max value" in {
    val max = 0
    val wrong = 1
    val v = Validator.max(max)
    v.validate(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v.validate(max) shouldBe empty
  }

  it should "validate for max value (exclusive)" in {
    val max = 0
    val wrong = 1
    val v = Validator.max(max, exclusive = true)
    v.validate(wrong) shouldBe List(ValidationError.Primitive(v, wrong))
    v.validate(max) shouldBe List(ValidationError.Primitive(v, max))
    v.validate(max - 1) shouldBe empty
  }

  it should "validate for maxSize of collection" in {
    val expected = 1
    val actual = List(1, 2, 3)
    val v = Validator.maxSize[Int, List](expected)
    v.validate(actual) shouldBe List(ValidationError.Primitive(v, actual))
    v.validate(List(1)) shouldBe empty
  }

  it should "validate for minSize of collection" in {
    val expected = 3
    val v = Validator.minSize[Int, List](expected)
    v.validate(List(1, 2)) shouldBe List(ValidationError.Primitive(v, List(1, 2)))
    v.validate(List(1, 2, 3)) shouldBe empty
  }

  it should "validate for matching regex pattern" in {
    val expected = "^apple$|^banana$"
    val wrong = "orange"
    Validator.pattern(expected).validate(wrong) shouldBe List(ValidationError.Primitive(Validator.pattern(expected), wrong))
    Validator.pattern(expected).validate("banana") shouldBe empty
  }

  it should "validate for minLength of string" in {
    val expected = 3
    val v = Validator.minLength[String](expected)
    v.validate("ab") shouldBe List(ValidationError.Primitive(v, "ab"))
    v.validate("abc") shouldBe empty
  }

  it should "validate for maxLength of string" in {
    val expected = 1
    val v = Validator.maxLength[String](expected)
    v.validate("ab") shouldBe List(ValidationError.Primitive(v, "ab"))
    v.validate("a") shouldBe empty
  }

  it should "validate with any of validators" in {
    val validator = Validator.any(Validator.max(5), Validator.max(10))
    validator.validate(4) shouldBe empty
    validator.validate(7) shouldBe empty
    validator.validate(11) shouldBe List(
      ValidationError.Primitive(Validator.max(5), 11),
      ValidationError.Primitive(Validator.max(10), 11)
    )
  }

  it should "validate with all of validators" in {
    val validator = Validator.all(Validator.min(3), Validator.max(10))
    validator.validate(4) shouldBe empty
    validator.validate(2) shouldBe List(ValidationError.Primitive(Validator.min(3), 2))
    validator.validate(11) shouldBe List(ValidationError.Primitive(Validator.max(10), 11))
  }

  it should "validate with custom validator" in {
    val v = Validator.custom(
      { x: Int =>
        if (x > 5) {
          List.empty
        } else {
          List(ValidationError.Custom(x, "X has to be greater than 5!"))
        }
      }
    )
    v.validate(0) shouldBe List(ValidationError.Custom(0, "X has to be greater than 5!"))
  }

  it should "validate openProduct" in {
    val validator = Validator.openProduct(Validator.min(10))
    validator.validate(Map("key" -> 0)).map(noPath(_)) shouldBe List(ValidationError.Primitive(Validator.min(10), 0))
    validator.validate(Map("key" -> 12)) shouldBe empty
  }

  it should "validate option" in {
    val validator = Validator.optionElement(Validator.min(10))
    validator.validate(None) shouldBe empty
    validator.validate(Some(12)) shouldBe empty
    validator.validate(Some(5)) shouldBe List(ValidationError.Primitive(Validator.min(10), 5))
  }

  it should "validate iterable" in {
    val validator = Validator.iterableElements[Int, List](Validator.min(10))
    validator.validate(List.empty[Int]) shouldBe empty
    validator.validate(List(11)) shouldBe empty
    validator.validate(List(5)) shouldBe List(ValidationError.Primitive(Validator.min(10), 5))
  }

  it should "validate array" in {
    val validator = Validator.arrayElements[Int](Validator.min(10))
    validator.validate(Array.empty[Int]) shouldBe empty
    validator.validate(Array(11)) shouldBe empty
    validator.validate(Array(5)) shouldBe List(ValidationError.Primitive(Validator.min(10), 5))
  }

  it should "validate product" in {
    case class Person(name: String, age: Int)
    implicit val nameValidator: Validator[String] = Validator.pattern("^[A-Z].*")
    implicit val ageValidator: Validator[Int] = Validator.min(18)
    val validator = Validator.derive[Person]
    validator.validate(Person("notImportantButOld", 21)).map(noPath(_)) shouldBe List(
      ValidationError.Primitive(Validator.pattern("^[A-Z].*"), "notImportantButOld")
    )
    validator.validate(Person("notImportantAndYoung", 15)).map(noPath(_)) shouldBe List(
      ValidationError.Primitive(Validator.pattern("^[A-Z].*"), "notImportantAndYoung"),
      ValidationError.Primitive(Validator.min(18), 15)
    )
    validator.validate(Person("ImportantButYoung", 15)).map(noPath(_)) shouldBe List(ValidationError.Primitive(Validator.min(18), 15))
    validator.validate(Person("ImportantAndOld", 21)) shouldBe empty
  }

  it should "validate enum" in {
    Validator.enum[Color].validate(Blue) shouldBe empty
  }

  it should "validate closed set of ints" in {
    val v = Validator.enum(List(1, 2, 3, 4))
    v.validate(1) shouldBe empty
    v.validate(0) shouldBe List(ValidationError.Primitive(v, 0))
  }

  it should "validate a custom case class" in {
    case class InnerCaseClass(innerValue: Long)
    case class MyClass(name: String, age: Int, field: InnerCaseClass)
    val validator = Validator.custom[MyClass](doValidate = { v =>
      val nameErrors =
        if (v.name.length < 3) List(ValidationError.Custom(v.name, "Name length should be >= 3", List(FieldName("name", "name"))))
        else List.empty
      val ageErrors =
        if (v.age <= 0) List(ValidationError.Custom(v.age, "Age should be > 0", List(FieldName("age", "age")))) else List.empty
      val innerErrors =
        if (v.field.innerValue <= 0)
          List(
            ValidationError.Custom(
              v.field.innerValue,
              "Inner value should be > 0",
              List(FieldName("field", "field"), FieldName("innerValue", "innerValue"))
            )
          )
        else List.empty
      nameErrors ++ ageErrors ++ innerErrors
    })

    validator.validate(MyClass("ab", -1, InnerCaseClass(-3))) shouldBe List(
      ValidationError.Custom("ab", "Name length should be >= 3", List(FieldName("name", "name"))),
      ValidationError.Custom(-1, "Age should be > 0", List(FieldName("age", "age"))),
      ValidationError.Custom(-3, "Inner value should be > 0", List(FieldName("field", "field"), FieldName("innerValue", "innerValue")))
    )
  }

  it should "skip collection validation for array if element validator is passing" in {
    val v = Validator.pass[Int]
    val bigArray = List.fill(1000000)(1).toArray
    val arrayValidator = v.asArrayElements

    // warm up
    (1 to 10).foreach { _ =>
      arrayValidator.validate(bigArray)
    }

    var summaryTime = 0L
    (1 to 100).foreach { _ =>
      val start = System.nanoTime()
      arrayValidator.validate(bigArray)
      val end = System.nanoTime()
      summaryTime += (end - start)
    }
    Duration(summaryTime, TimeUnit.NANOSECONDS).toSeconds should be <= 1L
  }

  it should "skip collection validation for iterable if element validator is passing" in {
    val v = Validator.pass[Int]
    val bigCollection = List.fill(1000000)(1)
    val collectionValidator = v.asIterableElements[List]
    // warm up
    (1 to 10).foreach { _ =>
      collectionValidator.validate(bigCollection)
    }

    var summaryTime = 0L
    (1 to 100).foreach { _ =>
      val start = System.nanoTime()
      collectionValidator.validate(bigCollection)
      val end = System.nanoTime()
      summaryTime += (end - start)
    }
    Duration(summaryTime, TimeUnit.NANOSECONDS).toSeconds should be <= 1L
  }

  it should "show recursive validators" in {
    import sttp.tapir.generic.auto.validator._
    val v: Validator[RecursiveName] = implicitly[Validator[RecursiveName]]
    v.show shouldBe Some("subNames->(elements(elements(recursive)))")
  }

  private def noPath[T](v: ValidationError[T]): ValidationError[T] =
    v match {
      case p: ValidationError.Primitive[T] => p.copy(path = Nil)
      case c: ValidationError.Custom[T]    => c.copy(path = Nil)
    }
}

sealed trait Color
case object Blue extends Color
case object Red extends Color

final case class RecursiveName(name: String, subNames: Option[Vector[RecursiveName]])
