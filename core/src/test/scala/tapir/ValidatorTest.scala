package tapir

import org.scalatest.{FlatSpec, Matchers}

class ValidatorTest extends FlatSpec with Matchers {
  it should "validate for min value" in {
    val expected = 1
    val wrong = 0
    Validator.min(expected).validate(wrong) shouldBe List(ValidationError(s"Expected $wrong to be greater than or equal to $expected"))
    Validator.min(expected).validate(expected) shouldBe empty
  }

  it should "validate for max value" in {
    val expected = 0
    val wrong = 1
    Validator.max(expected).validate(wrong) shouldBe List(ValidationError(s"Expected $wrong to be lower than or equal to $expected"))
    Validator.max(expected).validate(expected) shouldBe empty
  }

  it should "validate for maxSize of collection" in {
    val expected = 1
    val actual = List(1, 2, 3)
    Validator.maxSize(expected).validate(actual) shouldBe List(
      ValidationError(s"Expected collection size(${actual.size}) to be lower or equal to $expected")
    )
    Validator.maxSize(expected).validate(List(1)) shouldBe empty
  }

  it should "validate for minSize of collection" in {
    val expected = 3
    val actual = List(1, 2)
    Validator.minSize(expected).validate(actual) shouldBe List(
      ValidationError(s"Expected collection size(${actual.size}) to be greater or equal to $expected")
    )
    Validator.minSize(expected).validate(List(1, 2, 3)) shouldBe empty
  }

  it should "validate for matching regex pattern" in {
    val expected = "^apple$|^banana$"
    val wrong = "orange"
    Validator.pattern(expected).validate(wrong) shouldBe List(ValidationError(s"Expected '$wrong' to match '$expected'"))
    Validator.pattern(expected).validate("banana") shouldBe empty
  }

  it should "validate with any of validators" in {
    val validator = Validator.any(Validator.max(5), Validator.max(10))
    validator.validate(4) shouldBe empty
    validator.validate(7) shouldBe empty
    validator.validate(11) shouldBe List(
      ValidationError("Expected 11 to be lower than or equal to 5"),
      ValidationError("Expected 11 to be lower than or equal to 10")
    )
  }

  it should "validate with all of validators" in {
    val validator = Validator.all(Validator.min(3), Validator.max(10))
    validator.validate(4) shouldBe empty
    validator.validate(2) shouldBe List(ValidationError("Expected 2 to be greater than or equal to 3"))
    validator.validate(11) shouldBe List(ValidationError("Expected 11 to be lower than or equal to 10"))
  }

  it should "validate with custom validator" in {
    Validator
      .custom({ x: Int =>
        x > 5
      }, "X has to be greater than 5!")
      .validate(0) shouldBe List(ValidationError("Expected 0 to pass custom validation: X has to be greater than 5!"))
  }

  it should "validate openProduct" in {
    val validator = Validator.openProduct(Validator.min(10))
    validator.validate(Map("key" -> 0)) shouldBe List(ValidationError("Expected 0 to be greater than or equal to 10"))
    validator.validate(Map("key" -> 12)) shouldBe empty
  }

  it should "validate option" in {
    val validator = Validator.optionElement(Validator.min(10))
    validator.validate(None) shouldBe empty
    validator.validate(Some(12)) shouldBe empty
    validator.validate(Some(5)) shouldBe List(ValidationError("Expected 5 to be greater than or equal to 10"))
  }

  it should "validate iterable" in {
    val validator = Validator.iterableElements[Int, List](Validator.min(10))
    validator.validate(List.empty[Int]) shouldBe empty
    validator.validate(List(11)) shouldBe empty
    validator.validate(List(5)) shouldBe List(ValidationError("Expected 5 to be greater than or equal to 10"))
  }

  it should "validate array" in {
    val validator = Validator.arrayElements[Int](Validator.min(10))
    validator.validate(Array.empty[Int]) shouldBe empty
    validator.validate(Array(11)) shouldBe empty
    validator.validate(Array(5)) shouldBe List(ValidationError("Expected 5 to be greater than or equal to 10"))
  }

  it should "validate product" in {
    case class Person(name: String, age: Int)
    implicit val nameValidator: Validator[String] = Validator.pattern("^[A-Z].*")
    implicit val ageValidator: Validator[Int] = Validator.min(18)
    val validator = Validator.gen[Person]
    validator.validate(Person("notImportantButOld", 21)) shouldBe List(ValidationError("Expected 'notImportantButOld' to match '^[A-Z].*'"))
    validator.validate(Person("notImportantAndYoung", 15)) shouldBe List(
      ValidationError("Expected 'notImportantAndYoung' to match '^[A-Z].*'"),
      ValidationError("Expected 15 to be greater than or equal to 18")
    )
    validator.validate(Person("ImportantButYoung", 15)) shouldBe List(ValidationError("Expected 15 to be greater than or equal to 18"))
    validator.validate(Person("ImportantAndOld", 21)) shouldBe empty
  }
}
